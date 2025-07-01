import type {
	Attributes,
	Span as OtelSpan,
	SpanOptions,
} from '@opentelemetry/api'
import { type ResourceAttributes, Resource } from '@opentelemetry/resources'
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
			enableConsoleLogging: options.enableConsoleLogging ?? false,
			enableErrorTracking: options.enableErrorTracking ?? true,
			enablePerformanceMonitoring:
				options.enablePerformanceMonitoring ?? true,
			enableTracing: options.enableTracing ?? true,
			enableMetrics: options.enableMetrics ?? true,
			enableLogs: options.enableLogs ?? true,
			customHeaders: options.customHeaders ?? {},
			sessionTimeout: options.sessionTimeout ?? 30 * 60 * 1000,
			enableNativeCrashReporting:
				options.enableNativeCrashReporting ?? true,
			debug: options.debug ?? false,
		}
	}

	private async init() {
		if (this.isInitialized) return

		try {
			// Initialize session first
			await this.sessionManager.initialize()

			// Connect session manager to instrumentation manager
			this.instrumentationManager.setSessionManager(this.sessionManager)

			// Get session attributes for resource
			const sessionAttributes = this.sessionManager.getSessionAttributes()
			const resource = new Resource({
				[ATTR_SERVICE_NAME]: this.options.serviceName,
				'service.version': this.options.serviceVersion,
				'service.instance.id': this.sdkKey,
				...this.options.resourceAttributes,
				...sessionAttributes,
			})

			// Initialize instrumentation with resource
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
		if (!this.options.enableErrorTracking) return

		this.instrumentationManager.recordError(error, attributes, options)
	}

	public recordMetric(metric: Metric): void {
		if (!this.options.enableMetrics) return
		this.instrumentationManager.recordMetric(metric)
	}

	public recordCount(metric: Metric): void {
		if (!this.options.enableMetrics) return
		this.instrumentationManager.recordCount(metric)
	}

	public recordIncr(metric: Metric): void {
		if (!this.options.enableMetrics) return
		this.instrumentationManager.recordIncr(metric)
	}

	public recordHistogram(metric: Metric): void {
		if (!this.options.enableMetrics) return
		this.instrumentationManager.recordHistogram(metric)
	}

	public recordUpDownCounter(metric: Metric): void {
		if (!this.options.enableMetrics) return
		this.instrumentationManager.recordUpDownCounter(metric)
	}

	public async flush(): Promise<void> {
		return this.instrumentationManager.flush()
	}

	public log(message: any, level: string, attributes?: Attributes): void {
		if (!this.options.enableLogs) return
		this.instrumentationManager.recordLog(message, level, attributes)
	}

	public parseHeaders(headers: Record<string, string>): RequestContext {
		const sessionInfo = this.sessionManager.getSessionInfo()
		return {
			sessionId: headers['x-session-id'] || sessionInfo?.sessionId,
			requestId: headers['x-request-id'],
			userId: headers['x-user-id'] || sessionInfo?.userId,
			deviceId: sessionInfo?.deviceId,
		}
	}

	public runWithHeaders(
		name: string,
		headers: Record<string, string>,
		cb: (span: OtelSpan) => any,
		options?: SpanOptions,
	): any {
		if (!this.options.enableTracing) return cb({} as OtelSpan)
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
		if (!this.options.enableTracing) return {} as OtelSpan
		return this.instrumentationManager.startWithHeaders(
			spanName,
			headers,
			options,
		)
	}

	public startSpan(spanName: string, options?: SpanOptions): OtelSpan {
		if (!this.options.enableTracing) return {} as OtelSpan
		return this.instrumentationManager.startSpan(spanName, options)
	}

	public startActiveSpan<T>(
		spanName: string,
		fn: (span: OtelSpan) => T,
		options?: SpanOptions,
	): T {
		if (!this.options.enableTracing) return fn({} as OtelSpan)
		return this.instrumentationManager.startActiveSpan(
			spanName,
			fn,
			options,
		)
	}

	public async setUserId(userId: string): Promise<void> {
		await this.sessionManager.setUserId(userId)
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
