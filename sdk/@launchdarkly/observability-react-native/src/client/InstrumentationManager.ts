import {
	Attributes,
	Span as OtelSpan,
	SpanOptions,
	trace,
	propagation,
} from '@opentelemetry/api'
import { logs } from '@opentelemetry/api-logs'
import { metrics } from '@opentelemetry/api'
import { registerInstrumentations } from '@opentelemetry/instrumentation'
import { FetchInstrumentation } from '@opentelemetry/instrumentation-fetch'
import { XMLHttpRequestInstrumentation } from '@opentelemetry/instrumentation-xml-http-request'
import { OTLPTraceExporter } from '@opentelemetry/exporter-trace-otlp-http'
import { OTLPLogExporter } from '@opentelemetry/exporter-logs-otlp-http'
import { OTLPMetricExporter } from '@opentelemetry/exporter-metrics-otlp-http'
import {
	SpanProcessor,
	WebTracerProvider,
	ReadableSpan,
	BatchSpanProcessor,
	SpanExporter,
	BufferConfig,
} from '@opentelemetry/sdk-trace-web'
import {
	LoggerProvider,
	BatchLogRecordProcessor,
	LogRecordExporter,
} from '@opentelemetry/sdk-logs'
import {
	MeterProvider,
	PeriodicExportingMetricReader,
	PushMetricExporter,
} from '@opentelemetry/sdk-metrics'
import { Resource } from '@opentelemetry/resources'
import {
	ATTR_EXCEPTION_MESSAGE,
	ATTR_EXCEPTION_STACKTRACE,
	ATTR_EXCEPTION_TYPE,
} from '@opentelemetry/semantic-conventions'
import { SpanStatusCode } from '@opentelemetry/api'
import {
	W3CTraceContextPropagator,
	W3CBaggagePropagator,
	CompositePropagator,
} from '@opentelemetry/core'
import { ReactNativeOptions } from '../api/Options'
import { Metric } from '../api/Metric'
import {
	getCorsUrlsPattern,
	shouldNetworkRequestBeTraced,
} from '../utils/networkUtils'

export class CustomBatchSpanProcessor extends BatchSpanProcessor {
	private recentHttpSpans = new Map<string, number>()
	private readonly DEDUP_WINDOW_MS = 1000

	constructor(exporter: SpanExporter, options?: BufferConfig) {
		super(exporter, options)
	}

	onEnd(span: ReadableSpan): void {
		if (span.attributes['highlight.drop'] === true) {
			return
		}

		if (this.isHttpSpan(span)) {
			const spanKey = this.generateHttpSpanKey(span)
			const now = Date.now()

			this.cleanupOldHttpSpans(now)

			if (this.recentHttpSpans.has(spanKey)) {
				return // duplicate - skip
			}

			this.recentHttpSpans.set(spanKey, now)
			super.onEnd(span)

			return
		}

		super.onEnd(span)
	}

	private isHttpSpan(span: ReadableSpan): boolean {
		const url = span.attributes['http.url']
		const method = span.attributes['http.method']
		return Boolean(url && method)
	}

	private generateHttpSpanKey(span: ReadableSpan): string {
		const url = span.attributes['http.url'] as string
		const method = span.attributes['http.method'] as string
		const startTime = Math.floor(span.startTime[0])

		return `${method}:${url}:${startTime}`
	}

	private cleanupOldHttpSpans(now: number): void {
		for (const [key, timestamp] of this.recentHttpSpans.entries()) {
			if (now - timestamp > this.DEDUP_WINDOW_MS) {
				this.recentHttpSpans.delete(key)
			}
		}
	}
}

export class InstrumentationManager {
	private traceProvider?: WebTracerProvider
	private loggerProvider?: LoggerProvider
	private meterProvider?: MeterProvider
	private isInitialized = false
	private serviceName: string
	private resource: Resource = new Resource({})
	private headers: Record<string, string> = {}
	private traceExporter?: SpanExporter
	private logExporter?: LogRecordExporter
	private metricExporter?: PushMetricExporter

	constructor(private options: ReactNativeOptions) {
		this.serviceName =
			this.options.serviceName ??
			'launchdarkly-observability-react-native'
	}

	public initialize(resource: Resource) {
		if (this.isInitialized) return

		try {
			this.resource = resource
			this.headers = {
				...(this.options.customHeaders ?? {}),
			}

			this.initializeTracing()
			this.initializeLogs()
			this.initializeMetrics()

			this.isInitialized = true
			this._log('initialized successfully')
		} catch (error) {
			console.error('Failed to initialize InstrumentationManager:', error)
		}
	}

	private initializeTracing() {
		if (this.options.disableTraces) return

		const compositePropagator = new CompositePropagator({
			propagators: [
				new W3CTraceContextPropagator(),
				new W3CBaggagePropagator(),
			],
		})

		propagation.setGlobalPropagator(compositePropagator)

		const exporter =
			this.traceExporter ??
			new OTLPTraceExporter({
				url: `${this.options.otlpEndpoint}/v1/traces`,
				headers: this.headers,
			})

		const processors: SpanProcessor[] = [
			new CustomBatchSpanProcessor(exporter, {
				maxQueueSize: 100,
				scheduledDelayMillis: 500,
				exportTimeoutMillis: 5000,
				maxExportBatchSize: 10,
			}),
		]

		this.traceProvider = new WebTracerProvider({
			resource: this.resource,
			spanProcessors: processors,
		})

		this.traceProvider.register()
		trace.setGlobalTracerProvider(this.traceProvider)

		registerInstrumentations({
			instrumentations: [
				new FetchInstrumentation({
					propagateTraceHeaderCorsUrls: getCorsUrlsPattern(
						this.options.tracingOrigins,
					),
					requestHook: (span, request) => {
						const url = request.url
						if (
							!shouldNetworkRequestBeTraced(
								url,
								this.options.tracingOrigins ?? [],
								[],
							)
						) {
							span.setAttribute('highlight.drop', true)
						}
					},
				}),
				new XMLHttpRequestInstrumentation({
					propagateTraceHeaderCorsUrls: getCorsUrlsPattern(
						this.options.tracingOrigins,
					),
					requestHook: (span, xhr) => {
						const url = xhr.responseURL || ''
						if (
							!shouldNetworkRequestBeTraced(
								url,
								this.options.tracingOrigins ?? [],
								[],
							)
						) {
							span.setAttribute('highlight.drop', true)
						}
					},
				}),
			],
		})

		this._log('Tracing initialized')
	}

	private initializeLogs() {
		if (this.options.disableLogs) return

		const logExporter = new OTLPLogExporter({
			headers: this.headers,
			url: `${this.options.otlpEndpoint}/v1/logs`,
		})

		this.loggerProvider = new LoggerProvider({ resource: this.resource })

		this.loggerProvider.addLogRecordProcessor(
			new BatchLogRecordProcessor(logExporter, {
				maxQueueSize: 100,
				scheduledDelayMillis: 500,
				exportTimeoutMillis: 5000,
				maxExportBatchSize: 10,
			}),
		)

		logs.setGlobalLoggerProvider(this.loggerProvider)

		this._log('Logs initialized')
	}

	private initializeMetrics() {
		if (this.options.disableMetrics) return

		const metricExporter = new OTLPMetricExporter({
			headers: this.headers,
			url: `${this.options.otlpEndpoint}/v1/metrics`,
		})

		const readers = [
			new PeriodicExportingMetricReader({
				exporter: metricExporter,
				exportIntervalMillis: 10000,
				exportTimeoutMillis: 5000,
			}),
		]

		this.meterProvider = new MeterProvider({
			resource: this.resource,
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

			logger.emit({
				severityText: level.toUpperCase(),
				body:
					typeof message === 'string'
						? message
						: JSON.stringify(message),
				attributes: {
					...attributes,
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

	private getTracer() {
		return trace.getTracerProvider().getTracer(this.serviceName)
	}

	private getLogger() {
		return logs.getLoggerProvider().getLogger(this.serviceName)
	}

	private getMeter() {
		return metrics.getMeterProvider().getMeter(this.serviceName)
	}

	private _log(...data: any[]): void {
		if (this.options.debug) {
			console.log('[InstrumentationManager]', ...data)
		}
	}
}
