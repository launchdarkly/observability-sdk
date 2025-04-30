import {
	Attributes,
	Context,
	Counter,
	Gauge,
	Histogram,
	Span,
	SpanOptions,
	UpDownCounter,
} from '@opentelemetry/api'
import {
	getMeter,
	getTracer,
	LDClientMin,
	Metric,
	setupBrowserTracing,
} from 'client'
import type { Observe } from '../api/observe'
import { getNoopSpan } from '../client/otel/utils'
import {
	ErrorMessage,
	type ErrorMessageType,
} from '../client/types/shared-types'
import { parseError } from '../client/utils/errors'
import type { BrowserTracingConfig, Callback } from '../client/otel'
import { LaunchDarklyIntegration } from '../integrations/launchdarkly'
import { IntegrationClient } from '../integrations'

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
	}

	recordLog(message: any, level: string, metadata?: Attributes) {
		// TODO(vkorolik) use tracer to emit a log event
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
		// TODO(vkorolik) report via otel
		this._firstLoadListeners.errors.push(errorMsg)
		for (const integration of this._integrations) {
			integration.error(this.sessionSecureID, errorMsg)
		}
	}

	recordMetric(metric: Metric) {
		this.recordGauge({
			...metric,
			tags: [
				...(metric.tags ?? []),
				{ name: 'group', value: window.location.href },
			],
		})
	}

	recordCount(metric: Metric) {
		const meter = typeof getMeter === 'function' ? getMeter() : undefined
		if (!meter) return

		let counter = this._counters.get(metric.name)
		if (!counter) {
			counter = meter.createCounter(metric.name)
			this._counters.set(metric.name, counter)
		}
		counter.add(metric.value, {
			...metric.tags?.reduce((a, b) => ({ ...a, [b.name]: b.value }), {}),
			'highlight.session_id': this.sessionSecureID,
		})
	}

	recordGauge(metric: Metric) {
		const meter = typeof getMeter === 'function' ? getMeter() : undefined
		if (!meter) return

		let gauge = this._gauges.get(metric.name)
		if (!gauge) {
			gauge = meter.createGauge(metric.name)
			this._gauges.set(metric.name, gauge)
		}
		gauge.record(metric.value, {
			...metric.tags?.reduce((a, b) => ({ ...a, [b.name]: b.value }), {}),
			'highlight.session_id': this.sessionSecureID,
		})
	}

	recordIncr(metric: Omit<Metric, 'value'>) {
		this.recordCount({ ...metric, value: 1 })
	}

	recordHistogram(metric: Metric) {
		const meter = typeof getMeter === 'function' ? getMeter() : undefined
		if (!meter) return

		let histogram = this._histograms.get(metric.name)
		if (!histogram) {
			histogram = meter.createHistogram(metric.name)
			this._histograms.set(metric.name, histogram)
		}
		histogram.record(metric.value, {
			...metric.tags?.reduce((a, b) => ({ ...a, [b.name]: b.value }), {}),
			'highlight.session_id': this.sessionSecureID,
		})
	}

	recordUpDownCounter(metric: Metric) {
		const meter = typeof getMeter === 'function' ? getMeter() : undefined
		if (!meter) return

		let up_down_counter = this._up_down_counters.get(metric.name)
		if (!up_down_counter) {
			up_down_counter = meter.createUpDownCounter(metric.name)
			this._up_down_counters.set(metric.name, up_down_counter)
		}
		up_down_counter.add(metric.value, {
			...metric.tags?.reduce((a, b) => ({ ...a, [b.name]: b.value }), {}),
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

	register(client: LDClientMin) {
		// TODO(vkorolik) report metadata as resource attrs?
		this._integrations.push(new LaunchDarklyIntegration(client))
	}
}
