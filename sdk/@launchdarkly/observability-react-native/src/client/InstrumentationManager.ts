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
import { OTLPMetricExporter } from '@opentelemetry/exporter-metrics-otlp-http'
import {
	SpanProcessor,
	WebTracerProvider,
	ReadableSpan,
} from '@opentelemetry/sdk-trace-web'
import {
	LoggerProvider,
	BatchLogRecordProcessor,
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
import { W3CBaggagePropagator, CompositePropagator } from '@opentelemetry/core'
import { ReactNativeOptions } from '../api/Options'
import { Metric } from '../api/Metric'
import { SessionManager } from './SessionManager'
import {
	CustomSampler,
	CustomTraceContextPropagator,
	getCorsUrlsPattern,
	getSpanName,
	getSamplingConfig,
} from '@launchdarkly/observability-shared'
import { CustomBatchSpanProcessor } from '../otel/CustomBatchSpanProcessor'
import { CustomTraceExporter } from '../otel/CustomTraceExporter'
import { CustomLogExporter } from '../otel/CustomLogExporter'

export type InstrumentationManagerOptions = Required<ReactNativeOptions> & {
	projectId: string
}

export class InstrumentationManager {
	private traceProvider?: WebTracerProvider
	private loggerProvider?: LoggerProvider
	private meterProvider?: MeterProvider
	private isInitialized = false
	private serviceName: string
	private resource: Resource = new Resource({})
	private headers: Record<string, string> = {}
	private sessionManager?: SessionManager
	private sampler: CustomSampler = new CustomSampler()

	constructor(
		private options: Required<ReactNativeOptions> & {
			projectId: string
		},
	) {
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

			this.initializeSampling()
			this.initializeTracing()
			this.initializeLogs()
			this.initializeMetrics()

			this.isInitialized = true
			this._log('initialized successfully')
		} catch (error) {
			console.error('Failed to initialize InstrumentationManager:', error)
		}
	}

	public setSessionManager(sessionManager: SessionManager) {
		this.sessionManager = sessionManager
	}

	private initializeTracing() {
		if (this.options.disableTraces) return

		const compositePropagator = new CompositePropagator({
			propagators: [
				new W3CBaggagePropagator(),
				new CustomTraceContextPropagator({
					internalEndpoints: [
						this.options.backendUrl,
						`${this.options.otlpEndpoint}/v1/traces`,
						`${this.options.otlpEndpoint}/v1/logs`,
						`${this.options.otlpEndpoint}/v1/metrics`,
					],
					tracingOrigins: this.options.tracingOrigins,
					urlBlocklist: this.options.urlBlocklist,
				}),
			],
		})

		propagation.setGlobalPropagator(compositePropagator)

		const exporter = new CustomTraceExporter(
			{
				url: `${this.options.otlpEndpoint}/v1/traces`,
				headers: this.headers,
			},
			this.sampler,
		)

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

		const corsPattern = getCorsUrlsPattern(this.options.tracingOrigins)

		registerInstrumentations({
			instrumentations: [
				new FetchInstrumentation({
					applyCustomAttributesOnSpan: (span, request) => {
						if (!(span as any).attributes) {
							return
						}
						const readableSpan = span as unknown as ReadableSpan

						const url = readableSpan.attributes[
							'http.url'
						] as string
						const method = request.method ?? 'GET'

						span.updateName(getSpanName(url, method, request.body))
					},
					propagateTraceHeaderCorsUrls: corsPattern,
				}),
				new XMLHttpRequestInstrumentation({
					applyCustomAttributesOnSpan: (span, xhr) => {
						if (!(span as any).attributes) {
							return
						}
						const readableSpan = span as unknown as ReadableSpan

						try {
							const url = readableSpan.attributes[
								'http.url'
							] as string
							const method = readableSpan.attributes[
								'http.method'
							] as string
							let responseText: string | undefined
							if (['', 'text'].includes(xhr.responseType)) {
								responseText = xhr.responseText
							}
							span.updateName(
								getSpanName(url, method, responseText),
							)
						} catch (e) {
							console.error('Failed to update span name:', e)
						}
					},
					propagateTraceHeaderCorsUrls: corsPattern,
				}),
			],
		})

		this._log('Tracing initialized')
	}

	private async initializeSampling() {
		if (!this.options.projectId) return

		try {
			const samplingConfig = await getSamplingConfig(
				this.options.backendUrl,
				this.options.projectId,
			)
			this.sampler.setConfig(samplingConfig)
			this._log('Sampling configuration loaded')
		} catch (error) {
			console.warn('Failed to load sampling configuration:', error)
		}
	}

	private initializeLogs() {
		if (this.options.disableLogs) return

		const logExporter = new CustomLogExporter(
			{
				headers: this.headers,
				url: `${this.options.otlpEndpoint}/v1/logs`,
			},
			this.sampler,
		)

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
			const sessionId = this.sessionManager?.getSessionInfo().sessionId

			span.recordException(error)
			span.setAttribute(ATTR_EXCEPTION_MESSAGE, error.message)
			span.setAttribute(ATTR_EXCEPTION_TYPE, error.name ?? 'No name')
			if (error.stack) {
				span.setAttribute(ATTR_EXCEPTION_STACKTRACE, error.stack)
			}
			if (sessionId) {
				span.setAttribute('highlight.session_id', sessionId)
			}
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
				...(sessionId
					? {
							['highlight.session_id']: sessionId,
						}
					: {}),
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
			const sessionId = this.sessionManager?.getSessionInfo().sessionId

			logger.emit({
				severityText: level.toUpperCase(),
				body:
					typeof message === 'string'
						? message
						: JSON.stringify(message),
				attributes: {
					...attributes,
					'log.source': 'react-native-plugin',
					...(sessionId
						? {
								['highlight.session_id']: sessionId,
							}
						: {}),
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
