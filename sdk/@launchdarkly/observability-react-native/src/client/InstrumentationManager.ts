import {
	Attributes,
	Span as OtelSpan,
	SpanOptions,
	trace,
} from '@opentelemetry/api'
import { logs } from '@opentelemetry/api-logs'
import { metrics } from '@opentelemetry/api'
import { registerInstrumentations } from '@opentelemetry/instrumentation'
import { FetchInstrumentation } from '@opentelemetry/instrumentation-fetch'
import { OTLPTraceExporter } from '@opentelemetry/exporter-trace-otlp-http'
import { OTLPLogExporter } from '@opentelemetry/exporter-logs-otlp-http'
import { OTLPMetricExporter } from '@opentelemetry/exporter-metrics-otlp-http'
import {
	SimpleSpanProcessor,
	SpanProcessor,
	WebTracerProvider,
	ReadableSpan,
	Span,
} from '@opentelemetry/sdk-trace-web'
import {
	LoggerProvider,
	SimpleLogRecordProcessor,
} from '@opentelemetry/sdk-logs'
import {
	MeterProvider,
	PeriodicExportingMetricReader,
} from '@opentelemetry/sdk-metrics'
import { Resource } from '@opentelemetry/resources'
import {
	ATTR_EXCEPTION_MESSAGE,
	ATTR_EXCEPTION_STACKTRACE,
	ATTR_EXCEPTION_TYPE,
} from '@opentelemetry/semantic-conventions'
import { SpanStatusCode } from '@opentelemetry/api'
import { Context } from '@opentelemetry/api'
import { ReactNativeOptions } from '../api/Options'
import { Metric } from '../api/Metric'
import { SessionManager } from './SessionManager'

// Custom span processor to add session data to spans
class SessionSpanProcessor implements SpanProcessor {
	constructor(private sessionManager: SessionManager) {}

	onStart(span: Span, _parentContext: Context): void {
		const sessionInfo = this.sessionManager.getSessionInfo()

		if (sessionInfo) {
			span.setAttributes({
				'session.id': sessionInfo.sessionId,
				'session.device_id': sessionInfo.deviceId,
				'session.installation_id': sessionInfo.installationId,
				'session.start_time': sessionInfo.startTime,
				'session.duration_ms': (
					Date.now() - sessionInfo.startTime
				).toString(),
				'app.version': sessionInfo.appVersion,
				'device.platform': sessionInfo.platform,
			})
		}
	}

	onEnd(_span: ReadableSpan): void {
		// No-op for session processor
	}

	shutdown(): Promise<void> {
		return Promise.resolve()
	}

	forceFlush(): Promise<void> {
		return Promise.resolve()
	}
}

export class InstrumentationManager {
	private traceProvider?: WebTracerProvider
	private loggerProvider?: LoggerProvider
	private meterProvider?: MeterProvider
	private sessionManager?: SessionManager
	private isInitialized = false

	constructor(private options: Required<ReactNativeOptions>) {}

	public async initialize(resource: Resource): Promise<void> {
		if (this.isInitialized) return

		try {
			const headers = {
				'x-launchdarkly-dataset': this.options.serviceName,
				...this.options.customHeaders,
			}

			// Initialize tracing if enabled
			if (!this.options.disableTraces) {
				await this.initializeTracing(resource, headers)
			}

			// Initialize logging if enabled
			if (!this.options.disableLogs) {
				await this.initializeLogs(resource, headers)
			}

			// Initialize metrics if enabled
			if (!this.options.disableMetrics) {
				await this.initializeMetrics(resource, headers)
			}

			this.isInitialized = true
			this._log('InstrumentationManager initialized successfully')
		} catch (error) {
			console.error('Failed to initialize InstrumentationManager:', error)
		}
	}

	private async initializeTracing(
		resource: Resource,
		headers: Record<string, string>,
	): Promise<void> {
		if (this.options.disableTraces) return

		const exporter = new OTLPTraceExporter({
			url: `${this.options.otlpEndpoint}/v1/traces`,
			headers: {
				...headers,
				'x-launchdarkly-dataset': `${this.options.serviceName}-traces`,
			},
		})

		const processors: SpanProcessor[] = [new SimpleSpanProcessor(exporter)]

		if (this.sessionManager) {
			processors.push(new SessionSpanProcessor(this.sessionManager))
		}

		this.traceProvider = new WebTracerProvider({
			resource,
			spanProcessors: processors,
		})

		this.traceProvider.register()
		trace.setGlobalTracerProvider(this.traceProvider)

		registerInstrumentations({
			instrumentations: [
				new FetchInstrumentation({
					propagateTraceHeaderCorsUrls: /.*/,
					clearTimingResources: false,
				}),
			],
		})

		this._log('Tracing initialized')
	}

	private async initializeLogs(
		resource: Resource,
		headers: Record<string, string>,
	): Promise<void> {
		if (this.options.disableLogs) return

		const logExporter = new OTLPLogExporter({
			headers: {
				...headers,
				'x-launchdarkly-dataset': `${this.options.serviceName}-logs`,
			},
			url: `${this.options.otlpEndpoint}/v1/logs`,
		})

		this.loggerProvider = new LoggerProvider({ resource })

		this.loggerProvider.addLogRecordProcessor(
			new SimpleLogRecordProcessor(logExporter),
		)

		logs.setGlobalLoggerProvider(this.loggerProvider)

		this._log('Logs initialized')
	}

	private async initializeMetrics(
		resource: Resource,
		headers: Record<string, string>,
	): Promise<void> {
		if (this.options.disableMetrics) return

		const metricExporter = new OTLPMetricExporter({
			headers: {
				...headers,
				'x-launchdarkly-dataset': `${this.options.serviceName}-metrics`,
			},
			url: `${this.options.otlpEndpoint}/v1/metrics`,
		})

		const readers = [
			new PeriodicExportingMetricReader({ exporter: metricExporter }),
		]

		this.meterProvider = new MeterProvider({
			resource,
			readers,
		})

		metrics.setGlobalMeterProvider(this.meterProvider)

		this._log('Metrics initialized')
	}

	public recordError(
		error: Error,
		attributes?: Attributes,
		options?: { span: OtelSpan },
	): void {
		try {
			const activeSpan = options?.span || trace.getActiveSpan()
			const span = activeSpan ?? this.getTracer().startSpan('error')

			span.recordException(error)
			span.setAttribute(ATTR_EXCEPTION_MESSAGE, error.message)
			span.setAttribute(
				ATTR_EXCEPTION_STACKTRACE,
				error.stack ?? 'No stack trace',
			)
			span.setAttribute(ATTR_EXCEPTION_TYPE, error.name ?? 'No name')
			span.setStatus({ code: SpanStatusCode.ERROR })

			if (attributes) {
				span.setAttributes(attributes)
			}

			if (!activeSpan) {
				span.end()
			}

			this.recordLog(error.message, 'error', {
				...attributes,
				'exception.type': error.name,
				'exception.message': error.message,
				'exception.stacktrace': error.stack,
			})
		} catch (e) {
			console.error('Failed to record error:', e)
		}
	}

	public recordMetric(metric: Metric): void {
		try {
			const meter = this.getMeter()
			const counter = meter.createCounter(metric.name)
			counter.add(metric.value, metric.attributes)
		} catch (e) {
			console.error('Failed to record metric:', e)
		}
	}

	public recordCount(metric: Metric): void {
		this.recordMetric(metric)
	}

	public recordIncr(metric: Metric): void {
		const incrMetric = { ...metric, value: 1 }
		this.recordMetric(incrMetric)
	}

	public recordHistogram(metric: Metric): void {
		try {
			const meter = this.getMeter()
			const histogram = meter.createHistogram(metric.name)
			histogram.record(metric.value, metric.attributes)
		} catch (e) {
			console.error('Failed to record histogram:', e)
		}
	}

	public recordUpDownCounter(metric: Metric): void {
		try {
			const meter = this.getMeter()
			const upDownCounter = meter.createUpDownCounter(metric.name)
			upDownCounter.add(metric.value, metric.attributes)
		} catch (e) {
			console.error('Failed to record up/down counter:', e)
		}
	}

	public recordLog(
		message: any,
		level: string,
		attributes?: Attributes,
	): void {
		try {
			const logger = this.getLogger()
			const sessionContext =
				this.sessionManager?.getSessionContext() || {}

			logger.emit({
				severityText: level.toUpperCase(),
				body:
					typeof message === 'string'
						? message
						: JSON.stringify(message),
				attributes: {
					...attributes,
					...sessionContext,
					'log.source': 'react-native-plugin',
				},
				timestamp: Date.now(),
			})
		} catch (e) {
			console.error('Failed to record log:', e)
		}
	}

	public runWithHeaders(
		name: string,
		headers: Record<string, string>,
		cb: (span: OtelSpan) => any,
		options?: SpanOptions,
	): any {
		const tracer = this.getTracer()
		return tracer.startActiveSpan(name, options || {}, (span) => {
			try {
				Object.entries(headers).forEach(([key, value]) => {
					span.setAttribute(`http.header.${key}`, value)
				})

				return cb(span)
			} finally {
				span.end()
			}
		})
	}

	public startWithHeaders(
		spanName: string,
		headers: Record<string, string>,
		options?: SpanOptions,
	): OtelSpan {
		const tracer = this.getTracer()
		const span = tracer.startSpan(spanName, options)

		Object.entries(headers).forEach(([key, value]) => {
			span.setAttribute(`http.header.${key}`, value)
		})

		return span
	}

	public startSpan(spanName: string, options?: SpanOptions): OtelSpan {
		return this.getTracer().startSpan(spanName, options)
	}

	public startActiveSpan<T>(
		spanName: string,
		fn: (span: OtelSpan) => T,
		options?: SpanOptions,
	): T {
		return this.getTracer().startActiveSpan(spanName, options || {}, fn)
	}

	public async flush(): Promise<void> {
		try {
			if (this.traceProvider) {
				await this.traceProvider.forceFlush()
			}

			if (this.loggerProvider) {
				await this.loggerProvider.forceFlush()
			}

			if (this.meterProvider) {
				await this.meterProvider.forceFlush()
			}
		} catch (e) {
			console.error('Failed to flush telemetry:', e)
		}
	}

	public async stop(): Promise<void> {
		try {
			if (this.traceProvider) {
				await this.traceProvider.shutdown()
			}

			if (this.loggerProvider) {
				await this.loggerProvider.shutdown()
			}

			if (this.meterProvider) {
				await this.meterProvider.shutdown()
			}

			this.isInitialized = false
			this._log('InstrumentationManager stopped')
		} catch (e) {
			console.error('Failed to stop InstrumentationManager:', e)
		}
	}

	public setSessionManager(sessionManager: SessionManager): void {
		this.sessionManager = sessionManager
	}

	private getTracer() {
		return trace.getTracerProvider().getTracer(this.options.serviceName)
	}

	private getLogger() {
		return logs.getLoggerProvider().getLogger(this.options.serviceName)
	}

	private getMeter() {
		return metrics.getMeterProvider().getMeter(this.options.serviceName)
	}

	private _log(...data: any[]): void {
		if (this.options.debug) {
			console.log('[InstrumentationManager]', ...data)
		}
	}
}
