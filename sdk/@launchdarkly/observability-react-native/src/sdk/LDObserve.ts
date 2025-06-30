import { Attributes, Span as OtelSpan, SpanOptions } from '@opentelemetry/api'
import { ResourceAttributes } from '@opentelemetry/resources'
import { ObservabilityClient } from '../client/ObservabilityClient'
import { Observe } from '../api/Observe'
import { Metric } from '../api/Metric'
import { RequestContext } from '../api/RequestContext'

let observabilityClient: ObservabilityClient

export const _LDObserve = {
	_init(client: ObservabilityClient) {
		observabilityClient = client
	},

	isInitialized: () => {
		return !!observabilityClient && observabilityClient.getIsInitialized()
	},

	stop: async () => {
		if (!observabilityClient) {
			return
		}
		try {
			await observabilityClient.stop()
		} catch (e) {
			console.warn('observability-react-native stop error: ', e)
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
			console.warn('observability-react-native consumeError error: ', e)
		}
	},

	recordMetric: (metric: Metric) => {
		try {
			observabilityClient.recordMetric(metric)
		} catch (e) {
			console.warn('observability-react-native recordMetric error: ', e)
		}
	},

	recordCount: (metric: Metric) => {
		try {
			observabilityClient.recordCount(metric)
		} catch (e) {
			console.warn('observability-react-native recordCount error: ', e)
		}
	},

	recordIncr: (metric: Metric) => {
		try {
			observabilityClient.recordIncr(metric)
		} catch (e) {
			console.warn('observability-react-native recordIncr error: ', e)
		}
	},

	recordHistogram: (metric: Metric) => {
		try {
			observabilityClient.recordHistogram(metric)
		} catch (e) {
			console.warn(
				'observability-react-native recordHistogram error: ',
				e,
			)
		}
	},

	recordUpDownCounter: (metric: Metric) => {
		try {
			observabilityClient.recordUpDownCounter(metric)
		} catch (e) {
			console.warn(
				'observability-react-native recordUpDownCounter error: ',
				e,
			)
		}
	},

	flush: async () => {
		try {
			await observabilityClient.flush()
		} catch (e) {
			console.warn('observability-react-native flush error: ', e)
		}
	},

	recordLog: (
		message: any,
		level: string,
		secureSessionId?: string | undefined,
		requestId?: string | undefined,
		metadata?: Attributes,
	) => {
		try {
			observabilityClient.log(
				new Date(),
				message,
				level,
				{},
				secureSessionId,
				requestId,
				metadata,
			)
		} catch (e) {
			console.warn('observability-react-native log error: ', e)
		}
	},

	parseHeaders: (headers: Record<string, string>): RequestContext => {
		return observabilityClient.parseHeaders(headers)
	},

	runWithHeaders: (
		name: string,
		headers: Record<string, string>,
		cb: (span: OtelSpan) => any,
		options?: SpanOptions,
	) => {
		return observabilityClient.runWithHeaders(name, headers, cb, options)
	},

	startWithHeaders: (
		spanName: string,
		headers: Record<string, string>,
		options?: SpanOptions,
	) => {
		return observabilityClient.startWithHeaders(spanName, headers, options)
	},

	startSpan: (spanName: string, options?: SpanOptions) => {
		try {
			return observabilityClient.startSpan(spanName, options)
		} catch (e) {
			console.warn('observability-react-native startSpan error: ', e)
			// Return a no-op span to avoid breaking the application
			return {} as OtelSpan
		}
	},

	startActiveSpan: <T>(
		spanName: string,
		fn: (span: OtelSpan) => T,
		options?: SpanOptions,
	) => {
		try {
			return observabilityClient.startActiveSpan(spanName, fn, options)
		} catch (e) {
			console.warn(
				'observability-react-native startActiveSpan error: ',
				e,
			)
			// Run the function with a no-op span to avoid breaking the application
			return fn({} as OtelSpan)
		}
	},

	setResourceAttributes: (attributes: ResourceAttributes) => {
		return observabilityClient.setResourceAttributes(attributes)
	},

	setUserId: async (userId: string) => {
		try {
			await observabilityClient.setUserId(userId)
		} catch (e) {
			console.warn('observability-react-native setUserId error: ', e)
		}
	},

	getSessionInfo: () => {
		return observabilityClient.getSessionInfo()
	},

	_debug: (...data: any[]) => {
		observabilityClient._log(...data)
	},
}

// The _LDObserve object is for internal use.
// The LDObserve object is for external use and is exposed with an interface.

const LDObserve: Observe = _LDObserve as Observe

export { LDObserve }
