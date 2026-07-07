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
import { DEFAULT_URL_BLOCKLIST } from '../listeners/network-listener/utils/network-sanitizer'
import { Metric } from '../api/Metric'
import { RequestContext } from '../api/RequestContext'
import { TrackProperties } from '../api/TrackProperties'
import { SessionManager } from '../client/SessionManager'
import {
	InstrumentationManager,
	InstrumentationManagerOptions,
} from '../client/InstrumentationManager'
import { ErrorInstrumentation } from '../instrumentation/ErrorInstrumentation'

export class ObservabilityClient {
	private sessionManager: SessionManager
	private instrumentationManager: InstrumentationManager
	private errorInstrumentation?: ErrorInstrumentation
	private options: InstrumentationManagerOptions
	private isInitialized = false
	private initStarted = false
	private ldTracer?: LDTracer

	constructor(
		private readonly sdkKey: string,
		options: ReactNativeOptions = {},
	) {
		this.options = this.mergeOptions(sdkKey, options)
		this.sessionManager = new SessionManager(this.options)
		this.instrumentationManager = new InstrumentationManager(this.options)
		// Init is async (it resolves any persisted session from storage before
		// building the OTel resource). Callers observe readiness via
		// getIsInitialized(); LDObserve buffers calls until then.
		void this.init()
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
			sessionTimeout: options.sessionTimeout ?? 30 * 60 * 1000,
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
		}
	}

	private async init() {
		if (this.isInitialized || this.initStarted) return
		this.initStarted = true

		try {
			// Resolve (and possibly resume) the session before building the
			// resource, so the session id baked into the tracer resource is the
			// resumed id from the start.
			await this.sessionManager.initialize()

			const sessionAttributes = this.sessionManager.getSessionAttributes()
			const resource = resourceFromAttributes({
				[ATTR_SERVICE_NAME]: this.options.serviceName,
				[ATTR_SERVICE_VERSION]: this.options.serviceVersion,
				[ATTR_TELEMETRY_SDK_NAME]:
					'@launchdarkly/observability-react-native',
				[ATTR_TELEMETRY_SDK_VERSION]: this.options.serviceVersion,
				[ATTR_TELEMETRY_SDK_LANGUAGE]: 'javascript',
				// Old attribute for connecting to LD project. Can be deprecated in the
				// future in favor of X-LaunchDarkly-Project header.
				'highlight.project_id': this.sdkKey,
				'highlight.session_id': sessionAttributes.sessionId,
				...this.options.resourceAttributes,
				...sessionAttributes,
			})

			this.instrumentationManager.setSessionManager(this.sessionManager)
			await this.instrumentationManager.initialize(resource)

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
		} catch (error) {
			console.error('Failed to initialize ObservabilityClient:', error)
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

	/**
	 * Adopt a session id supplied by a native integration (session replay) that
	 * survived a JS soft reload. Forwarded to the session manager, which honors
	 * it when resolving the session during the in-flight async init. Must be
	 * called synchronously after construction (before init resolves) to take
	 * effect.
	 */
	public setPreferredSessionId(sessionId: string): void {
		this.sessionManager.setPreferredSessionId(sessionId)
	}

	public async stop(): Promise<void> {
		// Clean up error instrumentation
		if (this.errorInstrumentation) {
			this.errorInstrumentation.destroy()
			this.errorInstrumentation = undefined
		}

		await this.instrumentationManager.stop()
		this.ldTracer = undefined
		this.isInitialized = false
		this.initStarted = false
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
