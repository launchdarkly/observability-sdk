import type {
	Attributes,
	Context,
	Span as OtelSpan,
	SpanOptions,
} from '@opentelemetry/api'
import { LDTracer, wrapTracer } from '../api/LDTracer'
import { context } from '@opentelemetry/api'
import { NOOP_SPAN_OPS } from '../sdk/withSpan'
import { resourceFromAttributes } from '@opentelemetry/resources'
import {
	ATTR_SERVICE_NAME,
	ATTR_SERVICE_VERSION,
	ATTR_TELEMETRY_SDK_LANGUAGE,
	ATTR_TELEMETRY_SDK_NAME,
	ATTR_TELEMETRY_SDK_VERSION,
} from '@opentelemetry/semantic-conventions'
import { ReactNativeOptions } from '../api/Options'
import {
	DEFAULT_FLAG_EXPOSURE_DEDUPE_MAX_SIZE,
	DEFAULT_FLAG_EXPOSURE_DEDUPE_WINDOW_MILLIS,
} from '@launchdarkly/observability-shared'
import { DEFAULT_URL_BLOCKLIST } from '../listeners/network-listener/utils/network-sanitizer'
import { Metric } from '../api/Metric'
import { RequestContext } from '../api/RequestContext'
import { TrackProperties } from '../api/TrackProperties'
import { SessionManager } from '../client/SessionManager'
import { SESSION_RESUME_THRESHOLD_MS } from '../constants/sessions'
import {
	InstrumentationManager,
	InstrumentationManagerOptions,
} from '../client/InstrumentationManager'
import { ErrorInstrumentation } from '../instrumentation/ErrorInstrumentation'
import {
	DEFAULT_MAX_BUFFER_SIZE,
	DEFAULT_UPLOAD_INTERVAL_MILLIS,
} from '../constants/telemetry'

// Resource attribute carrying the deterministic symbols id (htlhash of the
// composed source map). The Metro plugin injects globalThis.__LD_SYMBOLS_ID__ at
// build time; reporting it here lets the backend resolve React Native symbols by
// symbols id (Symbols Id Lane), independent of the bundle filename or app
// version.
const ATTR_LAUNCHDARKLY_SYMBOLS_ID_HTLHASH = 'launchdarkly.symbols_id.htlhash'

// The Metro plugin reserves a fixed-length placeholder (32 zeros) and overwrites
// it in place with the real id. Seeing the placeholder (or nothing) means no
// usable symbols id was injected, so we omit the attribute and fall back to the
// Version Lane.
const SYMBOLS_ID_PLACEHOLDER = '0'.repeat(32)

function getInjectedSymbolsId(): string | undefined {
	const id = (globalThis as { __LD_SYMBOLS_ID__?: string }).__LD_SYMBOLS_ID__
	if (typeof id !== 'string' || id === '' || id === SYMBOLS_ID_PLACEHOLDER) {
		return undefined
	}
	return id
}

export class ObservabilityClient {
	private sessionManager: SessionManager
	private instrumentationManager: InstrumentationManager
	private errorInstrumentation?: ErrorInstrumentation
	private options: InstrumentationManagerOptions
	private isInitialized = false
	private initStarted = false
	// Set once stop() runs. init() is async, so a stop() can land while init()
	// is awaiting; this lets the in-flight init() abort instead of reviving a
	// torn-down client (re-enabling instrumentation, emitting app_reload, or
	// letting LDObserve load() a stopped client).
	private stopped = false
	// Resolved once init() reaches any terminal outcome — ready, stop-aborted, or
	// failed — with the final readiness. Lets callers (LDObserve) await readiness
	// instead of polling getIsInitialized() forever when init aborts or fails.
	private initSettled = false
	private initReadyResolvers: Array<(ready: boolean) => void> = []
	private ldTracer?: LDTracer

	constructor(
		private readonly sdkKey: string,
		options: ReactNativeOptions = {},
	) {
		this.options = this.mergeOptions(sdkKey, options)
		this.sessionManager = new SessionManager(this.options)
		this.instrumentationManager = new InstrumentationManager(this.options)
		// Init is async (it resolves any persisted session from storage before
		// building the OTel resource). Callers await readiness via
		// whenInitialized(); LDObserve buffers calls until then.
		void this.init()
	}

	/**
	 * Resolves when init() settles: `true` once the client is initialized, or
	 * `false` if init was aborted by stop() or failed. Never rejects and always
	 * settles, so callers can await readiness without polling forever.
	 */
	public whenInitialized(): Promise<boolean> {
		if (this.initSettled) {
			return Promise.resolve(this.isInitialized)
		}
		return new Promise<boolean>((resolve) => {
			this.initReadyResolvers.push(resolve)
		})
	}

	private settleInit(): void {
		if (this.initSettled) return
		this.initSettled = true
		const resolvers = this.initReadyResolvers
		this.initReadyResolvers = []
		for (const resolve of resolvers) {
			resolve(this.isInitialized)
		}
	}

	private mergeOptions(
		sdkKey: string,
		options: ReactNativeOptions,
	): InstrumentationManagerOptions {
		return {
			projectId: sdkKey,
			serviceName:
				options.serviceName ??
				'launchdarkly-observability-react-native',
			backendUrl:
				options.backendUrl ??
				'https://pub.observability.app.launchdarkly.com',
			otlpEndpoint:
				options.otlpEndpoint ??
				'https://otel.observability.app.launchdarkly.com:4318',
			serviceVersion: options.serviceVersion ?? '1.0.0',
			resourceAttributes: options.resourceAttributes ?? {},
			customHeaders: {
				// This is a newer method of connecting signals to an LD project.
				// Eventually will eliminate the need for the highlight.project_id attr.
				'X-LaunchDarkly-Project': this.sdkKey,
				...(options.customHeaders ?? {}),
			},
			sessionTimeout:
				options.sessionTimeout ?? SESSION_RESUME_THRESHOLD_MS,
			debug: options.debug ?? false,
			disableErrorTracking: options.disableErrorTracking ?? false,
			disableLogs: options.disableLogs ?? false,
			disableMetrics: options.disableMetrics ?? false,
			disableTraces: options.disableTraces ?? false,
			tracingOrigins: options.tracingOrigins ?? false,
			urlBlocklist: [
				...(options.urlBlocklist ?? []),
				...DEFAULT_URL_BLOCKLIST,
			],
			networkRecording: options.networkRecording ?? {},
			contextFriendlyName:
				options.contextFriendlyName ?? (() => undefined),
			flagExposureDedupeWindowMillis:
				options.flagExposureDedupeWindowMillis ??
				DEFAULT_FLAG_EXPOSURE_DEDUPE_WINDOW_MILLIS,
			flagExposureDedupeMaxSize:
				options.flagExposureDedupeMaxSize ??
				DEFAULT_FLAG_EXPOSURE_DEDUPE_MAX_SIZE,
			maxBufferSize: options.maxBufferSize ?? DEFAULT_MAX_BUFFER_SIZE,
			uploadIntervalMillis:
				options.uploadIntervalMillis ?? DEFAULT_UPLOAD_INTERVAL_MILLIS,
		}
	}

	private async init() {
		if (this.isInitialized || this.initStarted || this.stopped) return
		this.initStarted = true

		try {
			// Resolve (and possibly resume) the session before building the
			// resource, so the session id baked into the tracer resource is the
			// resumed id from the start.
			await this.sessionManager.initialize()

			// A stop() may have landed while we were awaiting; abort before
			// building any pipeline so we don't revive a torn-down client.
			if (this.stopped) {
				this.settleInit()
				return
			}

			const sessionAttributes = this.sessionManager.getSessionAttributes()
			const symbolsId = getInjectedSymbolsId()
			const resource = resourceFromAttributes({
				[ATTR_SERVICE_NAME]: this.options.serviceName,
				[ATTR_SERVICE_VERSION]: this.options.serviceVersion,
				[ATTR_TELEMETRY_SDK_NAME]:
					'@launchdarkly/observability-react-native',
				[ATTR_TELEMETRY_SDK_VERSION]: this.options.serviceVersion,
				[ATTR_TELEMETRY_SDK_LANGUAGE]: 'javascript',
				// Report the symbols id (Symbols Id Lane) only when the Metro plugin
				// injected a real one; otherwise omit it and let the backend fall back
				// to the Version Lane.
				...(symbolsId
					? {
							[ATTR_LAUNCHDARKLY_SYMBOLS_ID_HTLHASH]: symbolsId,
						}
					: {}),
				// Old attribute for connecting to LD project. Can be deprecated in the
				// future in favor of X-LaunchDarkly-Project header.
				'highlight.project_id': this.sdkKey,
				'highlight.session_id': sessionAttributes.sessionId,
				...this.options.resourceAttributes,
				...sessionAttributes,
			})

			this.instrumentationManager.setSessionManager(this.sessionManager)
			await this.instrumentationManager.initialize(resource)

			// If stop() ran while instrumentation was initializing, its
			// instrumentationManager.stop() may have run before this initialize()
			// finished — tear the freshly-built pipeline back down and abort so we
			// don't leave a live pipeline on a stopped client.
			if (this.stopped) {
				await this.instrumentationManager.stop()
				this.settleInit()
				return
			}

			// Initialize automatic error instrumentation (enabled by default)
			if (!this.options.disableErrorTracking) {
				this.errorInstrumentation = new ErrorInstrumentation(this)
				this.errorInstrumentation.initialize()
			}

			this.isInitialized = true

			// If this JS load resumed a previously persisted session (soft reload,
			// OTA reload, or quick relaunch within the resume window), emit an
			// `app_reload` span marking the boundary. Tracing is live at this point.
			if (this.sessionManager.wasReloaded()) {
				this.instrumentationManager.emitAppReload(
					this.sessionManager.getResumeInfo(),
					this.sessionManager.getSessionInfo().sessionId,
				)
			}

			this._log('initialized successfully')
			this.settleInit()
		} catch (error) {
			console.error('Failed to initialize ObservabilityClient:', error)
			// Release the guard so a later init() can retry; otherwise a single
			// failed attempt would permanently prevent this client from ever
			// becoming ready (initStarted stays true while isInitialized is false).
			this.initStarted = false
			// Settle readiness as not-ready so awaiters (e.g. LDObserve._init) don't
			// hang forever: init() is only ever driven from the constructor, so
			// nothing retries on our behalf, and a still-pending whenInitialized()
			// would never resolve. Callers that want to retry can start a new client.
			this.settleInit()
		}
	}

	public consumeCustomError(
		error: Error,
		attributes?: Attributes,
		options?: { span: OtelSpan },
	): void {
		if (this.options.disableErrorTracking) return
		this.instrumentationManager.recordError(error, attributes, options)
	}

	public recordMetric(metric: Metric): void {
		if (this.options.disableMetrics) return
		this.instrumentationManager.recordMetric(metric)
	}

	public recordCount(metric: Metric): void {
		if (this.options.disableMetrics) return
		this.instrumentationManager.recordCount(metric)
	}

	public recordIncr(metric: Metric): void {
		if (this.options.disableMetrics) return
		this.instrumentationManager.recordIncr(metric)
	}

	public recordHistogram(metric: Metric): void {
		if (this.options.disableMetrics) return
		this.instrumentationManager.recordHistogram(metric)
	}

	public recordUpDownCounter(metric: Metric): void {
		if (this.options.disableMetrics) return
		this.instrumentationManager.recordUpDownCounter(metric)
	}

	public async flush(): Promise<void> {
		return this.instrumentationManager.flush()
	}

	public recordLog(
		message: any,
		level: string,
		attributes?: Attributes,
	): void {
		if (this.options.disableLogs) return
		this.instrumentationManager.recordLog(message, level, attributes)
	}

	public track(
		key: string,
		properties?: TrackProperties,
		metricValue?: number,
	): void {
		if (this.options.disableTraces) return
		this.instrumentationManager.track(key, properties, metricValue)
	}

	public parseHeaders(headers: Record<string, string>): RequestContext {
		const sessionInfo = this.sessionManager.getSessionInfo()
		return {
			sessionId: headers['x-session-id'] || sessionInfo.sessionId,
			requestId: headers['x-request-id'],
		}
	}

	public runWithHeaders(
		name: string,
		headers: Record<string, string>,
		cb: (span: OtelSpan) => any,
		options?: SpanOptions,
	): any {
		if (this.options.disableTraces) {
			return cb({} as OtelSpan)
		}

		return this.instrumentationManager.runWithHeaders(
			name,
			headers,
			cb,
			options,
		)
	}

	public startWithHeaders(
		spanName: string,
		headers: Record<string, string>,
		options?: SpanOptions,
	): OtelSpan {
		if (this.options.disableTraces) {
			return {} as OtelSpan
		}

		return this.instrumentationManager.startWithHeaders(
			spanName,
			headers,
			options,
		)
	}

	public startSpan(
		spanName: string,
		options?: SpanOptions,
		ctx?: Context,
	): OtelSpan {
		if (this.options.disableTraces) {
			return {} as OtelSpan
		}

		return this.instrumentationManager.startSpan(spanName, options, ctx)
	}

	public startActiveSpan<T>(
		spanName: string,
		fn: (span: OtelSpan) => T,
		options?: SpanOptions,
		ctx?: Context,
	): T {
		if (this.options.disableTraces) {
			return fn({} as OtelSpan)
		}

		return this.instrumentationManager.startActiveSpan(
			spanName,
			options || {},
			ctx || context.active(),
			fn,
		)
	}

	public getTracer(): LDTracer {
		if (!this.ldTracer) {
			if (this.options.disableTraces) {
				this.ldTracer = wrapTracer(NOOP_SPAN_OPS)
			} else {
				this.ldTracer = wrapTracer({
					startSpan: (name, options, ctx) =>
						this.instrumentationManager.startSpan(
							name,
							options,
							ctx,
						),
					startActiveSpan: (name, fn, options, ctx) =>
						this.instrumentationManager.startActiveSpan(
							name,
							options || {},
							ctx || context.active(),
							fn,
						),
					recordError: (error, attributes, options) =>
						this.consumeCustomError(error, attributes, options),
				})
			}
		}
		return this.ldTracer
	}

	public getContextFromSpan(span: OtelSpan): Context {
		return this.instrumentationManager.getContextFromSpan(span)
	}

	public getSessionInfo(): any {
		return this.sessionManager.getSessionInfo()
	}

	public async stop(): Promise<void> {
		// Signal any in-flight async init() to abort instead of reviving the
		// client after teardown (see init() and the `stopped` field).
		this.stopped = true

		// Clean up error instrumentation
		if (this.errorInstrumentation) {
			this.errorInstrumentation.destroy()
			this.errorInstrumentation = undefined
		}

		await this.instrumentationManager.stop()
		this.ldTracer = undefined
		this.isInitialized = false
		this.initStarted = false
		// Resolve any pending whenInitialized() awaiters as not-ready so they stop
		// waiting on a client that has been torn down.
		this.settleInit()
	}

	public getIsInitialized(): boolean {
		return this.isInitialized
	}

	public _log(...data: any[]): void {
		if (this.options.debug) {
			console.log('[ObservabilityClient]', ...data)
		}
	}
}
