import {
	Attributes,
	Context,
	Counter,
	Gauge,
	Histogram,
	metrics,
	Span,
	SpanOptions,
	SpanStatusCode,
	UpDownCounter,
} from '@opentelemetry/api'
import { BrowserTracingConfig, Callback, LOG_SPAN_NAME } from '../client/otel'
import {
	BROWSER_METER_NAME,
	getTracer,
	setupBrowserTracing,
} from '../client/otel'
import type { LDClientMin } from '../integrations/launchdarkly/types/LDClient'
import type { Observe } from '../api/observe'
import { getNoopSpan } from '../client/otel/utils'
import {
	ConsoleMessage,
	ErrorMessage,
	ErrorMessageType,
} from '../client/types/shared-types'
import { parseError } from '../client/utils/errors'
import { LaunchDarklyIntegration } from '../integrations/launchdarkly'
import type { IntegrationClient } from '../integrations'
import type { OTelMetric as Metric } from '../client/types/types'
import { ConsoleMethods, MetricCategory } from '../client/types/client'
import { ObserveOptions } from '../client/types/observe'
import { ConsoleListener } from '../client/listeners/console-listener'
import stringify from 'json-stringify-safe'
import {
	ERROR_PATTERNS_TO_IGNORE,
	ERRORS_TO_IGNORE,
} from '../client/constants/errors'
import { ErrorListener } from '../client/listeners/error-listener'
import { LDPluginEnvironmentMetadata } from '../plugins/plugin'
import { Hook } from '../integrations/launchdarkly/types/Hooks'
import {
	PerformanceListener,
	PerformancePayload,
} from '../client/listeners/performance-listener/performance-listener'
import { LDObserve } from './LDObserve'
import {
	JankListener,
	JankPayload,
} from '../client/listeners/jank-listener/jank-listener'

export class ObserveSDK implements Observe {
	private readonly sessionSecureID: string
	private readonly _integrations: IntegrationClient[] = []
	private readonly _gauges: Map<string, Gauge> = new Map<string, Gauge>()
	private readonly _counters: Map<string, Counter> = new Map<
		string,
		Counter
	>()
	private readonly _histograms: Map<string, Histogram> = new Map<
		string,
		Histogram
	>()
	private readonly _up_down_counters: Map<string, UpDownCounter> = new Map<
		string,
		UpDownCounter
	>()
	constructor(options: BrowserTracingConfig) {
		this.sessionSecureID = options.sessionSecureId
		setupBrowserTracing(options)
		this.setupListeners(options)
	}

	recordLog(message: any, level: ConsoleMethods, metadata?: Attributes) {
		this.startSpan(LOG_SPAN_NAME, (span) => {
			span?.setAttributes({
				'highlight.session_id': this.sessionSecureID,
			})
			span?.addEvent('log', {
				'log.severity': level,
				'log.message': message,
				...metadata,
			})
			if (level === 'error') {
				span?.recordException(new Error(message))
				span?.setStatus({
					code: SpanStatusCode.ERROR,
					message: message,
				})
			}
		})
	}

	recordError(
		error: Error,
		message?: string,
		payload?: { [key: string]: string },
		source?: string,
		type?: ErrorMessageType,
	) {
		if (error.cause) {
			payload = {
				...payload,
				'exception.cause': JSON.stringify(error.cause),
			}
		}
		let event = message ? message + ':' + error.message : error.message
		if (type === 'React.ErrorBoundary') {
			event = 'ErrorBoundary: ' + event
		}
		const res = parseError(error)
		const errorMsg: ErrorMessage = {
			event,
			type: type ?? 'custom',
			url: window.location.href,
			source: source ?? '',
			lineNumber: res[0]?.lineNumber ? res[0]?.lineNumber : 0,
			columnNumber: res[0]?.columnNumber ? res[0]?.columnNumber : 0,
			stackTrace: res,
			timestamp: new Date().toISOString(),
			payload: JSON.stringify(payload),
		}
		this.startSpan('highlight.exception', (span) => {
			span?.recordException(error)
			span?.setAttributes({
				event: errorMsg.event,
				type: errorMsg.type,
				url: errorMsg.url,
				source: errorMsg.source,
				lineNumber: errorMsg.lineNumber,
				columnNumber: errorMsg.columnNumber,
				payload: errorMsg.payload,
			})
		})
		for (const integration of this._integrations) {
			integration.error(this.sessionSecureID, errorMsg)
		}
	}

	recordCount(metric: Metric) {
		const meter = metrics.getMeter(BROWSER_METER_NAME)
		let counter = this._counters.get(metric.name)
		if (!counter) {
			counter = meter.createCounter(metric.name)
			this._counters.set(metric.name, counter)
		}
		counter.add(metric.value, {
			...metric.attributes,
			'highlight.session_id': this.sessionSecureID,
		})
	}

	recordGauge(metric: Metric) {
		const meter = metrics.getMeter(BROWSER_METER_NAME)
		let gauge = this._gauges.get(metric.name)
		if (!gauge) {
			gauge = meter.createGauge(metric.name)
			this._gauges.set(metric.name, gauge)
		}
		gauge.record(metric.value, {
			...metric.attributes,
			'highlight.session_id': this.sessionSecureID,
		})
	}

	recordIncr(metric: Omit<Metric, 'value'>) {
		this.recordCount({ ...metric, value: 1 })
	}

	recordHistogram(metric: Metric) {
		const meter = metrics.getMeter(BROWSER_METER_NAME)
		let histogram = this._histograms.get(metric.name)
		if (!histogram) {
			histogram = meter.createHistogram(metric.name)
			this._histograms.set(metric.name, histogram)
		}
		histogram.record(metric.value, {
			...metric.attributes,
			'highlight.session_id': this.sessionSecureID,
		})
	}

	recordUpDownCounter(metric: Metric) {
		const meter = metrics.getMeter(BROWSER_METER_NAME)
		let up_down_counter = this._up_down_counters.get(metric.name)
		if (!up_down_counter) {
			up_down_counter = meter.createUpDownCounter(metric.name)
			this._up_down_counters.set(metric.name, up_down_counter)
		}
		up_down_counter.add(metric.value, {
			...metric.attributes,
			'highlight.session_id': this.sessionSecureID,
		})
	}

	startSpan(
		name: string,
		options: SpanOptions | ((span?: Span) => any),
		context?: Context | ((span?: Span) => any),
		fn?: (span?: Span) => any,
	) {
		const tracer = typeof getTracer === 'function' ? getTracer() : undefined
		if (!tracer) {
			const noopSpan = getNoopSpan()

			if (fn === undefined && context === undefined) {
				return (options as Callback)(noopSpan)
			} else if (fn === undefined) {
				return (context as Callback)(noopSpan)
			} else {
				return fn(noopSpan)
			}
		}

		const wrapCallback = (span: Span, callback: (span: Span) => any) => {
			const result = callback(span)
			if (result instanceof Promise) {
				return result.finally(() => span.end())
			} else {
				span.end()
				return result
			}
		}

		if (fn === undefined && context === undefined) {
			return tracer.startActiveSpan(name, (span) =>
				wrapCallback(span, options as Callback),
			)
		} else if (fn === undefined) {
			return tracer.startActiveSpan(
				name,
				options as SpanOptions,
				(span) => wrapCallback(span, context as Callback),
			)
		} else {
			return tracer.startActiveSpan(
				name,
				options as SpanOptions,
				context as Context,
				(span) => wrapCallback(span, fn),
			)
		}
	}

	startManualSpan(
		name: string,
		options: SpanOptions | ((span: Span) => any),
		context?: Context | ((span: Span) => any),
		fn?: (span: Span) => any,
	) {
		const tracer = typeof getTracer === 'function' ? getTracer() : undefined
		if (!tracer) {
			const noopSpan = getNoopSpan()

			if (fn === undefined && context === undefined) {
				return (options as Callback)(noopSpan)
			} else if (fn === undefined) {
				return (context as Callback)(noopSpan)
			} else {
				return fn(noopSpan)
			}
		}

		if (fn === undefined && context === undefined) {
			return tracer.startActiveSpan(name, options as Callback)
		} else if (fn === undefined) {
			return tracer.startActiveSpan(
				name,
				options as SpanOptions,
				context as Callback,
			)
		} else {
			return tracer.startActiveSpan(
				name,
				options as SpanOptions,
				context as Context,
				fn,
			)
		}
	}

	register(client: LDClientMin, metadata: LDPluginEnvironmentMetadata) {
		this._integrations.push(new LaunchDarklyIntegration(client, metadata))
	}

	getHooks(metadata: LDPluginEnvironmentMetadata): Hook[] {
		return this._integrations.flatMap((i) => i.getHooks?.(metadata) ?? [])
	}

	private setupListeners(options: ObserveOptions) {
		if (!options.disableConsoleRecording) {
			ConsoleListener(
				(c: ConsoleMessage) => {
					const payload: {
						type: string
						url?: string
						source?: string
						lineNumber?: string
						columnNumber?: string
						stackTrace?: string
					} = {
						...(c.attributes ? { attributes: c.attributes } : {}),
						type: c.type,
					}
					if (
						options.reportConsoleErrors &&
						(c.type === 'Error' || c.type === 'error') &&
						c.value &&
						c.trace
					) {
						const errorValue = stringify(c.value)
						if (
							ERRORS_TO_IGNORE.includes(errorValue) ||
							ERROR_PATTERNS_TO_IGNORE.some((pattern) =>
								errorValue.includes(pattern),
							)
						) {
							return
						}
						const err = new Error(errorValue)
						err.stack = stringify(c.trace.map((s) => s.toString()))
						payload.url = window.location.href
						payload.source = c.trace[0]?.fileName
						payload.lineNumber = c.trace[0]?.lineNumber?.toString()
						payload.columnNumber =
							c.trace[0]?.columnNumber?.toString()
						this.recordError(
							err,
							undefined,
							payload,
							payload.source,
							'console.error',
						)
					}
					this.recordLog(stringify(c.value), 'error', {})
				},
				{
					level: options.consoleMethodsToRecord ?? [],
					logger: 'console',
					stringifyOptions: {
						depthOfLimit: 10,
						numOfKeysLimit: 100,
						stringLengthLimit: 1000,
					},
				},
			)
		}
		if (options.enablePerformanceRecording !== false) {
			PerformanceListener((payload: PerformancePayload) => {
				Object.entries(payload)
					.filter(([name]) => name !== 'relativeTimestamp')
					.forEach(
						([name, value]) =>
							value &&
							LDObserve.recordGauge({
								name,
								value,
								attributes: {
									category: MetricCategory.Performance,
									group: window.location.href,
								},
							}),
					)
			}, 0)
			JankListener((payload: JankPayload) => {
				LDObserve.recordGauge({
					name: 'Jank',
					value: payload.jankAmount,
					attributes: {
						category: MetricCategory.WebVital,
						group: payload.querySelector,
					},
				})
			}, 0)
		}
		ErrorListener(
			(e: ErrorMessage) => {
				if (
					ERRORS_TO_IGNORE.includes(e.event) ||
					ERROR_PATTERNS_TO_IGNORE.some((pattern) =>
						e.event.includes(pattern),
					)
				) {
					return
				}
				const err = new Error(e.event)
				err.stack = stringify(e.stackTrace.map((s) => s.toString()))
				this.recordError(
					err,
					e.event,
					{
						...(e.payload ? { payload: e.payload } : {}),
						lineNumber: e.lineNumber.toString(),
						columnNumber: e.columnNumber.toString(),
						source: e.source,
						url: e.url,
					},
					e.source,
					e.type,
				)
			},
			{ enablePromisePatch: !!options.enablePromisePatch },
		)
	}
}
