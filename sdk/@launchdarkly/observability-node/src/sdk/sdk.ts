import {
	Attributes,
	Span as OtelSpan,
} from '@opentelemetry/api'
import { ResourceAttributes } from '@opentelemetry/resources'
import { ObservabilityClient } from '../client/ObservabilityClient'
import { NodeOptions } from '../api/Options'
import { RequestContext } from '../api/RequestContext'
import { Observe } from '../api/Observe'

export const HIGHLIGHT_REQUEST_HEADER = 'x-highlight-request'

export function makeSDK(observabilityClient: ObservabilityClient): Observe {

	return {
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
		recordMetric: (metric) => {
			try {
				observabilityClient.recordMetric(metric)
			} catch (e) {
				console.warn('highlight-node recordMetric error: ', e)
			}
		},
		recordCount: (metric) => {
			try {
				observabilityClient.recordCount(metric)
			} catch (e) {
				console.warn('highlight-node recordCount error: ', e)
			}
		},
		recordIncr: (metric) => {
			try {
				observabilityClient.recordIncr(metric)
			} catch (e) {
				console.warn('highlight-node recordIncr error: ', e)
			}
		},
		recordHistogram: (metric) => {
			try {
				observabilityClient.recordHistogram(metric)
			} catch (e) {
				console.warn('highlight-node recordHistogram error: ', e)
			}
		},
		recordUpDownCounter: (metric) => {
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
		parseHeaders: (headers): RequestContext => {
			return observabilityClient.parseHeaders(headers)
		},

		runWithHeaders: (name, headers, cb, options) => {
			return observabilityClient.runWithHeaders(name, headers, cb, options)
		},
		startWithHeaders: (spanName, headers, options) => {
			return observabilityClient.startWithHeaders(spanName, headers, options)
		},
		setAttributes: (attributes: ResourceAttributes) => {
			return observabilityClient.setAttributes(attributes)
		},
	}
}
