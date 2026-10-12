import { Attributes, Span as OtelSpan, SpanOptions } from '@opentelemetry/api'
import { ObservabilityClient } from '../client/ObservabilityClient'

import { RequestContext } from '../api/RequestContext'
import { Observe } from '../api/Observe'
import { Metric } from '../api/Metric'
import { Headers, IncomingHttpHeaders } from '../api/headers'

/**
 * Bundlers (e.g. Turbopack in Next.js) may include a separate copy of this
 * module in each server bundle, which would otherwise split the `LDObserve`
 * singleton across copies: `instrumentation.ts` would initialize one copy
 * while pages/route handlers happen to hold an uninitialized one. Persisting
 * the client on `globalThis` makes the singleton shared across every copy in
 * the process (and HMR re-runs).
 */
const GLOBAL_CLIENT_KEY = '__launchdarklyObservabilityNodeClient'

function readGlobalObservabilityClient(): ObservabilityClient | undefined {
	return (globalThis as unknown as Record<string, unknown>)[
		GLOBAL_CLIENT_KEY
	] as ObservabilityClient | undefined
}

function getClient(): ObservabilityClient {
	return readGlobalObservabilityClient() as ObservabilityClient
}

const _LDObserve = {
	/**
	 * Install the singleton client. Idempotent: once a client is installed (by
	 * any of the module's copies), later calls are no-ops — a second bundle
	 * copy's `plugin.register` can never replace (or double-start) the live
	 * OpenTelemetry configuration.
	 */
	_init(client: ObservabilityClient): ObservabilityClient {
		if (!readGlobalObservabilityClient()) {
			;(globalThis as unknown as Record<string, unknown>)[
				GLOBAL_CLIENT_KEY
			] = client
		}
		return getClient()
	},
	isInitialized: () => {
		return !!readGlobalObservabilityClient()
	},
	stop: async () => {
		if (!readGlobalObservabilityClient()) {
			return
		}
		try {
			await getClient().stop()
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
			getClient()?.consumeCustomError(
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
			getClient()?.recordMetric(metric)
		} catch (e) {
			console.warn('highlight-node recordMetric error: ', e)
		}
	},
	recordCount: (metric: Metric) => {
		try {
			getClient()?.recordCount(metric)
		} catch (e) {
			console.warn('highlight-node recordCount error: ', e)
		}
	},
	recordIncr: (metric: Omit<Metric, 'value'>) => {
		try {
			getClient()?.recordIncr(metric)
		} catch (e) {
			console.warn('highlight-node recordIncr error: ', e)
		}
	},
	recordHistogram: (metric: Metric) => {
		try {
			getClient()?.recordHistogram(metric)
		} catch (e) {
			console.warn('highlight-node recordHistogram error: ', e)
		}
	},
	recordUpDownCounter: (metric: Metric) => {
		try {
			getClient()?.recordUpDownCounter(metric)
		} catch (e) {
			console.warn('highlight-node recordUpDownCounter error: ', e)
		}
	},
	flush: async () => {
		try {
			await getClient()?.flush()
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
			getClient()?.log(
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
		return getClient().parseHeaders(headers)
	},
	runWithHeaders: (
		name: string,
		headers: Headers | IncomingHttpHeaders,
		cb: (span: OtelSpan) => any,
		options?: SpanOptions,
	) => {
		return getClient().runWithHeaders(name, headers, cb, options)
	},
	startWithHeaders: (
		spanName: string,
		headers: Headers | IncomingHttpHeaders,
		options?: SpanOptions,
	) => {
		return getClient().startWithHeaders(spanName, headers, options)
	},
	setAttributes: (attributes: Attributes) => {
		return getClient().setAttributes(attributes)
	},
	_debug: (...data: any[]) => {
		getClient()?._log(...data)
	},
}

// The _LDObserve object is for internal use.
// The LDObserve object is for external use and is exposed with an interface.

const LDObserve: Observe = _LDObserve as unknown as Observe

export { LDObserve, _LDObserve }
