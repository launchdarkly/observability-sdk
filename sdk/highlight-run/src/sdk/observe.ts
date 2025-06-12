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
import {
	BROWSER_METER_NAME,
	Callback,
	getTracer,
	LOG_SPAN_NAME,
	setupBrowserTracing,
} from '../client/otel'
import type { Observe } from '../api/observe'
import { getNoopSpan } from '../client/otel/utils'
import {
	ConsoleMessage,
	ErrorMessage,
	ErrorMessageType,
} from '../client/types/shared-types'
import { parseError } from '../client/utils/errors'
import {
	type Hook,
	LaunchDarklyIntegration,
	type LDClient,
} from '../integrations/launchdarkly'
import type { IntegrationClient } from '../integrations'
import type { OTelMetric as Metric, RecordMetric } from '../client/types/types'
import {
	ALL_CONSOLE_METHODS,
	ConsoleMethods,
	MetricCategory,
	MetricName,
} from '../client/types/client'
import { ConsoleListener } from '../client/listeners/console-listener'
import stringify from 'json-stringify-safe'
import {
	ERROR_PATTERNS_TO_IGNORE,
	ERRORS_TO_IGNORE,
} from '../client/constants/errors'
import { ErrorListener } from '../client/listeners/error-listener'
import { LDPluginEnvironmentMetadata } from '../plugins/plugin'
import {
	PerformanceListener,
	PerformancePayload,
} from '../client/listeners/performance-listener/performance-listener'
import {
	JankListener,
	JankPayload,
} from '../client/listeners/jank-listener/jank-listener'
import { ExportSampler } from '../client/otel/sampling/ExportSampler'
import { CustomSampler } from '../client/otel/sampling/CustomSampler'
import { getSdk, Sdk } from '../client/graph/generated/operations'
import { GraphQLClient } from 'graphql-request'
import { getGraphQLRequestWrapper } from '../client/utils/graph'
import { internalLog } from './util'
import { getPersistentSessionSecureID } from '../client/utils/sessionStorage/highlightSession'
import { WebVitalsListener } from '../client/listeners/web-vitals-listener/web-vitals-listener'
import { getPerformanceMethods } from '../client/utils/performance/performance'
import {
	ViewportResizeListener,
	type ViewportResizeListenerArgs,
} from '../client/listeners/viewport-resize-listener'
import {
	NetworkPerformanceListener,
	NetworkPerformancePayload,
} from '../client/listeners/network-listener/performance-listener'
import { ObserveOptions } from '../client/types/observe'

export class ObserveSDK implements Observe {
	/** Verbose project ID that is exposed to users. Legacy users may still be using ints. */
	organizationID!: string
	private readonly _options: ObserveOptions
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
	private readonly sampler: ExportSampler = new CustomSampler()
	private graphqlSDK!: Sdk
	constructor(
		options: ObserveOptions & {
			projectId: string
			sessionSecureId: string
		},
	) {
		this._options = options
		this.organizationID = options.projectId
		setupBrowserTracing(
			{
				...{
					backendUrl:
						options?.backendUrl ??
						'https://pub.observability.app.launchdarkly.com',
					otlpEndpoint:
						options?.otel?.otlpEndpoint ??
						'https://otel.observability.app.launchdarkly.com',
					projectId: options.projectId,
					sessionSecureId: options.sessionSecureId,
					environment: options?.environment ?? 'production',
					networkRecordingOptions:
						typeof options?.networkRecording === 'object'
							? options.networkRecording
							: undefined,
					tracingOrigins: options?.tracingOrigins,
					serviceName: options?.serviceName ?? 'browser',
					instrumentations: options?.otel?.instrumentations,
					eventNames: options?.otel?.eventNames,
				},
				getIntegrations: () => this._integrations,
			},
			this.sampler,
		)
		const client = new GraphQLClient(`${options.backendUrl}`, {
			headers: {},
		})
		this.graphqlSDK = getSdk(client, getGraphQLRequestWrapper())
		this.configureSampling()
		this.setupListeners()
	}

	private async configureSampling() {
		try {
			const res = await this.graphqlSDK.GetSamplingConfig({
				organization_verbose_id: `${this.organizationID}`,
			})
			this.sampler.setConfig(res.sampling)
		} catch (e) {
			internalLog(
				'sampling',
				'warn',
				`LaunchDarkly Observability: Failed to configure sampling: ${e}`,
			)
		}
	}

	private _recordLog(
		message: any,
		level: ConsoleMethods,
		metadata?: Attributes,
		trace?: StackTrace.StackFrame[],
	) {
		this.startSpan(LOG_SPAN_NAME, (span) => {
			const msg =
				typeof message === 'string' ? message : stringify(message)
			const stackTrace = trace
				? stringify(trace.map((s) => s.toString()))
				: undefined
			span?.addEvent('log', {
				'log.severity': level,
				'log.message': msg,
				'code.stacktrace': stackTrace,
				...metadata,
			})
			if (this._options.reportConsoleErrors && level === 'error') {
				span?.recordException(new Error(msg))
				span?.setStatus({
					code: SpanStatusCode.ERROR,
					message: msg,
				})
				const err = new Error(msg)
				if (trace) {
					err.stack = stackTrace
				}
				this.recordError(err)
			}
		})
	}

	recordLog(message: any, level: ConsoleMethods, metadata?: Attributes) {
		return this._recordLog(message, level, metadata)
	}

	recordError(
		error: any,
		message?: string,
		payload?: { [key: string]: string },
		source?: string,
		type?: ErrorMessageType,
	) {
		if (error instanceof Error && error.cause) {
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
			error,
			event,
			type: type ?? 'custom',
			url: window.location.href,
			source: source ?? '',
			lineNumber: res[0]?.lineNumber ? res[0]?.lineNumber : 0,
			columnNumber: res[0]?.columnNumber ? res[0]?.columnNumber : 0,
			stackTrace: res,
			timestamp: new Date().toISOString(),
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
				...payload,
			})
		})
		for (const integration of this._integrations) {
			integration.error(getPersistentSessionSecureID(), errorMsg)
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
			'highlight.session_id': getPersistentSessionSecureID(),
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
			'highlight.session_id': getPersistentSessionSecureID(),
		})
		const recordMetric: RecordMetric = {
			name: metric.name,
			value: metric.value,
			category: metric.attributes?.['category'] as MetricCategory,
			group: metric.attributes?.['group']?.toString(),
			tags: metric.attributes
				? Object.entries(metric.attributes).map(([key, value]) => ({
						name: key ?? '',
						value: value?.toString() ?? '',
					}))
				: [],
		}
		for (const integration of this._integrations) {
			integration.recordGauge(
				getPersistentSessionSecureID(),
				recordMetric,
			)
		}
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
			'highlight.session_id': getPersistentSessionSecureID(),
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
			'highlight.session_id': getPersistentSessionSecureID(),
		})
	}

	startSpan(
		name: string,
		options: SpanOptions | ((span?: Span) => any),
		context?: Context | ((span?: Span) => any),
		fn?: (span?: Span) => any,
	) {
		const tracer = getTracer()
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
		const tracer = getTracer()
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

	register(client: LDClient, metadata: LDPluginEnvironmentMetadata) {
		this._integrations.push(new LaunchDarklyIntegration(client, metadata))
	}

	getHooks(metadata: LDPluginEnvironmentMetadata): Hook[] {
		return this._integrations.flatMap((i) => i.getHooks?.(metadata) ?? [])
	}

	private submitViewportMetrics({
		height,
		width,
		availHeight,
		availWidth,
	}: ViewportResizeListenerArgs) {
		this.recordGauge({
			name: MetricName.ViewportHeight,
			value: height,
			attributes: {
				category: MetricCategory.Device,
				group: window.location.href,
			},
		})
		this.recordGauge({
			name: MetricName.ViewportWidth,
			value: width,
			attributes: {
				category: MetricCategory.Device,
				group: window.location.href,
			},
		})
		this.recordGauge({
			name: MetricName.ScreenHeight,
			value: availHeight,
			attributes: {
				category: MetricCategory.Device,
				group: window.location.href,
			},
		})
		this.recordGauge({
			name: MetricName.ScreenWidth,
			value: availWidth,
			attributes: {
				category: MetricCategory.Device,
				group: window.location.href,
			},
		})
		this.recordGauge({
			name: MetricName.ViewportArea,
			value: height * width,
			attributes: {
				category: MetricCategory.Device,
				group: window.location.href,
			},
		})
	}

	private setupListeners() {
		if (!this._options.disableConsoleRecording) {
			ConsoleListener(
				(c: ConsoleMessage) => {
					this._recordLog(
						c.value?.join(' '),
						c.type as ConsoleMethods,
						c.attributes ? JSON.parse(c.attributes) : {},
						c.trace,
					)
				},
				{
					level: this._options.consoleMethodsToRecord ?? [
						...ALL_CONSOLE_METHODS,
					],
					logger: 'console',
					stringifyOptions: {
						depthOfLimit: 10,
						numOfKeysLimit: 100,
						stringLengthLimit: 1000,
					},
				},
			)
		}
		if (this._options.enablePerformanceRecording !== false) {
			PerformanceListener((payload: PerformancePayload) => {
				Object.entries(payload)
					.filter(([name]) => name !== 'relativeTimestamp')
					.forEach(
						([name, value]) =>
							value &&
							this.recordGauge({
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
				this.recordGauge({
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
				let payload: { [key: string]: string } = {}
				try {
					if (e.payload) {
						payload = JSON.parse(e.payload)
					}
				} catch (e) {}
				this.recordError(
					e.error ?? err,
					e.event,
					{
						...payload,
						lineNumber: e.lineNumber.toString(),
						columnNumber: e.columnNumber.toString(),
						source: e.source,
						url: e.url,
					},
					e.source,
					e.type,
				)
			},
			{ enablePromisePatch: !!this._options.enablePromisePatch },
		)
		WebVitalsListener((data) => {
			const { name, value } = data
			this.recordGauge({
				name,
				value,
				attributes: {
					group: window.location.href,
					category: MetricCategory.WebVital,
				},
			})
		})
		ViewportResizeListener((viewport: ViewportResizeListenerArgs) => {
			this.submitViewportMetrics(viewport)
		})
		NetworkPerformanceListener((payload: NetworkPerformancePayload) => {
			const attributes: Attributes = {
				category: MetricCategory.Performance,
				group: window.location.href,
			}
			if (payload.saveData !== undefined) {
				attributes['saveData'] = payload.saveData.toString()
			}
			if (payload.effectiveType !== undefined) {
				attributes['effectiveType'] = payload.effectiveType.toString()
			}
			if (payload.type !== undefined) {
				attributes['type'] = payload.type.toString()
			}
			Object.entries(payload)
				.filter(([name]) => name !== 'relativeTimestamp')
				.forEach(
					([name, value]) =>
						value &&
						typeof value === 'number' &&
						this.recordGauge({
							name,
							value: value as number,
							attributes,
						}),
				)
		}, new Date().getTime())

		const { getDeviceDetails } = getPerformanceMethods()
		if (getDeviceDetails) {
			this.recordGauge({
				name: MetricName.DeviceMemory,
				value: getDeviceDetails().deviceMemory,
				attributes: {
					category: MetricCategory.Device,
					group: window.location.href,
				},
			})
		}
	}
}
