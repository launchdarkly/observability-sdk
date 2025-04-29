import { Context, Span, SpanOptions } from '@opentelemetry/api'
import { getMeter, Metric } from 'client'
import type { Observability } from '../api/observability'
import { getNoopSpan } from '../client/otel/utils'
import { MetricCategory } from '../index'
import {
	ErrorMessage,
	type ErrorMessageType,
} from '../client/types/shared-types'
import { parseError } from '../client/utils/errors'

export class ObservabilitySDK implements Observability {
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
			integration.error(this.sessionData.sessionSecureID, errorMsg)
		}
	}
	metrics(metrics: Metric[]) {
		for (const m of metrics) {
			this.recordMetric(m)
		}
	}
	recordMetric(metric: Metric) {
		this.recordGauge({
			...metric,
			tags: metric.tags ?? [],
			group: window.location.href,
			category: MetricCategory.Frontend,
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
			group: metric.group,
			category: metric.category,
			'highlight.session_id': this.sessionData.sessionSecureID,
		})
	}

	recordIncr(metric: Omit<Metric, 'value'>) {
		try {
			H.onHighlightReady(() => {
				highlight_obj.recordIncr(metric)
			})
		} catch (e) {
			HighlightWarning('recordIncr', e)
		}
	}

	recordHistogram(metric: Metric) {
		try {
			H.onHighlightReady(() => {
				highlight_obj.recordHistogram(metric)
			})
		} catch (e) {
			HighlightWarning('recordHistogram', e)
		}
	}
	recordUpDownCounter(metric: Metric) {
		try {
			H.onHighlightReady(() => {
				highlight_obj.recordUpDownCounter(metric)
			})
		} catch (e) {
			HighlightWarning('recordUpDownCounter', e)
		}
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
}
