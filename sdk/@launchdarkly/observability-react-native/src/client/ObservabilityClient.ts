import type {
	Attributes,
	Span as OtelSpan,
	SpanOptions,
} from '@opentelemetry/api'
import { resourceFromAttributes } from '@opentelemetry/resources'
import {
	ATTR_SERVICE_NAME,
	ATTR_SERVICE_VERSION,
	ATTR_TELEMETRY_SDK_LANGUAGE,
	ATTR_TELEMETRY_SDK_NAME,
	ATTR_TELEMETRY_SDK_VERSION,
} from '@opentelemetry/semantic-conventions'
import { ReactNativeOptions } from '../api/Options'
import { Metric } from '../api/Metric'
import { RequestContext } from '../api/RequestContext'
import { SessionManager } from '../client/SessionManager'
import { InstrumentationManager } from '../client/InstrumentationManager'

export class ObservabilityClient {
	private sessionManager: SessionManager
	private instrumentationManager: InstrumentationManager
	private options: Required<ReactNativeOptions>
	private isInitialized = false

	constructor(
		private readonly sdkKey: string,
		options: ReactNativeOptions = {},
	) {
		this.options = this.mergeOptions(options)
		this.sessionManager = new SessionManager(this.options)
		this.instrumentationManager = new InstrumentationManager(this.options)
		this.init()
	}

	private mergeOptions(
		options: ReactNativeOptions,
	): Required<ReactNativeOptions> {
		return {
			otlpEndpoint:
				options.otlpEndpoint ??
				'https://otel.observability.app.launchdarkly.com:4318',
			serviceName:
				options.serviceName ??
				'launchdarkly-observability-react-native',
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
			urlBlocklist: options.urlBlocklist ?? [],
		}
	}

	private init() {
		if (this.isInitialized) return

		try {
			this.sessionManager.initialize()

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
			})

			this.instrumentationManager.setSessionManager(this.sessionManager)
			this.instrumentationManager.initialize(resource)
			this.isInitialized = true

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

	public log(message: any, level: string, attributes?: Attributes): void {
		if (this.options.disableLogs) return
		this.instrumentationManager.recordLog(message, level, attributes)
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

	public startSpan(spanName: string, options?: SpanOptions): OtelSpan {
		if (this.options.disableTraces) {
			return {} as OtelSpan
		}

		return this.instrumentationManager.startSpan(spanName, options)
	}

	public startActiveSpan<T>(
		spanName: string,
		fn: (span: OtelSpan) => T,
		options?: SpanOptions,
	): T {
		if (this.options.disableTraces) {
			return fn({} as OtelSpan)
		}

		return this.instrumentationManager.startActiveSpan(
			spanName,
			fn,
			options,
		)
	}

	public getSessionInfo(): any {
		return this.sessionManager.getSessionInfo()
	}

	public async stop(): Promise<void> {
		await this.instrumentationManager.stop()
		this.isInitialized = false
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
