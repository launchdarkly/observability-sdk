import type {
	Attributes,
	Span as OtelSpan,
	SpanOptions,
} from '@opentelemetry/api'
import { Resource } from '@opentelemetry/resources'
import { ATTR_SERVICE_NAME } from '@opentelemetry/semantic-conventions'
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
				options.otlpEndpoint ?? 'https://otlp.highlight.io:4318',
			serviceName: options.serviceName ?? 'react-native-app',
			serviceVersion: options.serviceVersion ?? '1.0.0',
			resourceAttributes: options.resourceAttributes ?? {},
			customHeaders: options.customHeaders ?? {},
			sessionTimeout: options.sessionTimeout ?? 30 * 60 * 1000,
			debug: options.debug ?? false,
			disableErrorTracking: options.disableErrorTracking ?? false,
			disableLogs: options.disableLogs ?? false,
			disableMetrics: options.disableMetrics ?? false,
			disableTraces: options.disableTraces ?? false,
		}
	}

	private async init() {
		if (this.isInitialized) return

		try {
			await this.sessionManager.initialize()
			this.instrumentationManager.setSessionManager(this.sessionManager)

			const sessionAttributes = this.sessionManager.getSessionAttributes()
			const resource = new Resource({
				[ATTR_SERVICE_NAME]: this.options.serviceName,
				'service.version': this.options.serviceVersion,
				'highlight.project_id': this.sdkKey, // For connection to LD project
				...this.options.resourceAttributes,
				...sessionAttributes,
			})

			await this.instrumentationManager.initialize(resource)

			this.isInitialized = true
			this._log('ObservabilityClient initialized successfully')
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
