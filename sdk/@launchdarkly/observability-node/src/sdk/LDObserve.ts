import { Attributes, Span as OtelSpan, SpanOptions } from '@opentelemetry/api'
import { ResourceAttributes } from '@opentelemetry/resources'
import { ObservabilityClient } from '../client/ObservabilityClient'

import { RequestContext } from '../api/RequestContext'
import { Headers, IncomingHttpHeaders, Observe } from '../api/Observe'
import { Metric } from '../api/Metric'

export const HIGHLIGHT_REQUEST_HEADER = 'x-highlight-request'

let observabilityClient: ObservabilityClient

const _LDObserve = {
	_init(client: ObservabilityClient) {
		observabilityClient = client
	},
	isInitialized: () => {
		return !!observabilityClient
	},
	stop: async () => {
		if (!observabilityClient) {
			return
		}
		try {
			await observabilityClient.stop()
		} catch (e) {
			console.warn('highlight-node stop error: ', e)
		}
	},
	recordError: (
		error: Error,
		secureSessionId?: string,
		requestId?: string,
		metadata?: Attributes,
		options?: { span: OtelSpan },
	) => {
		try {
			observabilityClient?.consumeCustomError(
				error,
				secureSessionId,
				requestId,
				metadata,
				options,
			)
		} catch (e) {
			console.warn('highlight-node consumeError error: ', e)
		}
	},
	recordMetric: (metric: Metric) => {
		try {
			observabilityClient.recordMetric(metric)
		} catch (e) {
			console.warn('highlight-node recordMetric error: ', e)
		}
	},
	recordCount: (metric: Metric) => {
		try {
			observabilityClient.recordCount(metric)
		} catch (e) {
			console.warn('highlight-node recordCount error: ', e)
		}
	},
	recordIncr: (metric: Metric) => {
		try {
			observabilityClient.recordIncr(metric)
		} catch (e) {
			console.warn('highlight-node recordIncr error: ', e)
		}
	},
	recordHistogram: (metric: Metric) => {
		try {
			observabilityClient.recordHistogram(metric)
		} catch (e) {
			console.warn('highlight-node recordHistogram error: ', e)
		}
	},
	recordUpDownCounter: (metric: Metric) => {
		try {
			observabilityClient.recordUpDownCounter(metric)
		} catch (e) {
			console.warn('highlight-node recordUpDownCounter error: ', e)
		}
	},
	flush: async () => {
		try {
			await observabilityClient.flush()
		} catch (e) {
			console.warn('highlight-node flush error: ', e)
		}
	},
	recordLog: (
		message: any,
		level: string,
		secureSessionId?: string | undefined,
		requestId?: string | undefined,
		metadata?: Attributes,
	) => {
		const o: { stack: any } = { stack: {} }
		Error.captureStackTrace(o)
		try {
			observabilityClient.log(
				new Date(),
				message,
				level,
				o.stack,
				secureSessionId,
				requestId,
				metadata,
			)
		} catch (e) {
			console.warn('highlight-node log error: ', e)
		}
	},
	parseHeaders: (headers: Headers | IncomingHttpHeaders): RequestContext => {
		return observabilityClient.parseHeaders(headers)
	},

	runWithHeaders: (
		name: string,
		headers: Headers | IncomingHttpHeaders,
		cb: (span: OtelSpan) => any,
		options?: SpanOptions,
	) => {
		return observabilityClient.runWithHeaders(name, headers, cb, options)
	},
	startWithHeaders: (
		spanName: string,
		headers: Headers | IncomingHttpHeaders,
		options?: SpanOptions,
	) => {
		return observabilityClient.startWithHeaders(spanName, headers, options)
	},
	setAttributes: (attributes: ResourceAttributes) => {
		return observabilityClient.setAttributes(attributes)
	},
	_debug: (...data: any[]) => {
		observabilityClient._log(...data)
	},
}

// The _LDObserve object is for internal use.
// The LDObserve object is for external use and is exposed with an interface.

const LDObserve: Observe = _LDObserve as Observe

export { LDObserve, _LDObserve }
