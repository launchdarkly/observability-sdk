import {
	Attributes,
	context,
	Context,
	Span as OtelSpan,
	SpanOptions,
} from '@opentelemetry/api'
import { wrapTracer } from '../api/LDTracer'
import type { LDTracer } from '../api/LDTracer'
import { ObservabilityClient } from '../client/ObservabilityClient'
import { Metric } from '../api/Metric'
import { RequestContext } from '../api/RequestContext'
import { Observe } from '../api/Observe'
import { SpanScope, WithSpanOptions } from '../api/SpanScope'
import { TrackProperties } from '../api/TrackProperties'
import { BufferedClass } from './BufferedClass'
import { noOpSpan } from '../utils/NoOpSpan'
import { NOOP_SPAN_OPS, runInSpan, SpanOps } from './withSpan'

class LDObserveClass
	extends BufferedClass<ObservabilityClient>
	implements Observe
{
	private _lazyTracer?: LDTracer
	// Captured at _init time, before async initialization completes, so the
	// (provisional) session id can be read synchronously.
	private _earlyClient?: ObservabilityClient

	private _resolveTracer(): LDTracer {
		if (this._isLoaded) {
			return this._sdk.getTracer()
		}
		return wrapTracer(NOOP_SPAN_OPS)
	}

	private _getLazyTracer(): LDTracer {
		if (!this._lazyTracer) {
			this._lazyTracer = {
				startSpan: (name, options, ctx) =>
					this._resolveTracer().startSpan(name, options, ctx),
				startActiveSpan: ((name: string, ...rest: unknown[]) =>
					(this._resolveTracer().startActiveSpan as Function)(
						name,
						...rest,
					)) as LDTracer['startActiveSpan'],
				withSpan: (name, fn, options) =>
					this._resolveTracer().withSpan(name, fn, options),
			}
		}
		return this._lazyTracer
	}

	_resetForTesting(): void {
		this._lazyTracer = undefined
		this._earlyClient = undefined
		super._resetForTesting()
	}

	recordError(
		error: Error,
		attributes?: Attributes,
		options?: { span: OtelSpan },
	): void {
		return this._bufferCall('consumeCustomError', [
			error,
			attributes,
			options,
		])
	}

	recordMetric(metric: Metric): void {
		return this._bufferCall('recordMetric', [metric])
	}

	recordCount(metric: Metric): void {
		return this._bufferCall('recordCount', [metric])
	}

	recordIncr(metric: Metric): void {
		return this._bufferCall('recordIncr', [metric])
	}

	recordHistogram(metric: Metric): void {
		return this._bufferCall('recordHistogram', [metric])
	}

	recordUpDownCounter(metric: Metric): void {
		return this._bufferCall('recordUpDownCounter', [metric])
	}

	flush(): Promise<void> {
		return this._bufferCall('flush', []) || Promise.resolve()
	}

	recordLog(message: any, level: string, attributes?: Attributes): void {
		return this._bufferCall('recordLog', [message, level, attributes])
	}

	track(
		key: string,
		properties?: TrackProperties,
		metricValue?: number,
	): void {
		return this._bufferCall('track', [key, properties, metricValue])
	}

	parseHeaders(headers: Record<string, string>): RequestContext {
		const response = this._bufferCall('parseHeaders', [headers])
		return this._isLoaded
			? response
			: {
					sessionId: headers['x-session-id'],
					requestId: headers['x-request-id'],
				}
	}

	runWithHeaders(
		name: string,
		headers: Record<string, string>,
		cb: (span: OtelSpan) => any,
		options?: SpanOptions,
	): any {
		const response = this._bufferCall('runWithHeaders', [
			name,
			headers,
			cb,
			options,
		])
		return this._isLoaded
			? response
			: cb(noOpSpan.setAttribute('method', 'runWithHeaders'))
	}

	startWithHeaders(
		spanName: string,
		headers: Record<string, string>,
		options?: SpanOptions,
	): OtelSpan {
		const response = this._bufferCall('startWithHeaders', [
			spanName,
			headers,
			options,
		])
		return this._isLoaded
			? response
			: noOpSpan.setAttribute('method', 'startWithHeaders')
	}

	startSpan(
		spanName: string,
		options?: SpanOptions,
		ctx?: Context,
	): OtelSpan {
		const response = this._bufferCall('startSpan', [spanName, options, ctx])
		return this._isLoaded
			? response
			: noOpSpan.setAttribute('method', 'startSpan')
	}

	startActiveSpan<T>(
		spanName: string,
		fn: (span: OtelSpan) => T,
		options?: SpanOptions,
		ctx?: Context,
	): T {
		const response = this._bufferCall('startActiveSpan', [
			spanName,
			fn,
			options,
			ctx,
		])

		return this._isLoaded
			? response
			: fn(noOpSpan.setAttribute('method', 'startActiveSpan'))
	}

	withSpan<T>(
		spanName: string,
		fn: (scope: SpanScope) => T,
		options?: WithSpanOptions,
	): T {
		// When loaded, `this` routes recordError through consumeCustomError (respects
		// disableErrorTracking). Before init we use no-op span ops.
		const ops: SpanOps = this._isLoaded ? this : NOOP_SPAN_OPS
		return runInSpan(ops, spanName, fn, options)
	}

	getTracer(): LDTracer {
		return this._getLazyTracer()
	}

	getContextFromSpan(span: OtelSpan): Context {
		const response = this._bufferCall('getContextFromSpan', [span])
		return this._isLoaded ? response : context.active()
	}

	getSessionInfo(): any {
		if (this._isLoaded) {
			return this._bufferCall('getSessionInfo', [])
		}
		// Init is async, but the session id is resolved (at least provisionally)
		// when the client is constructed — before init finishes. Expose it early
		// so integrations that register right after observability (e.g. session
		// replay) can adopt the same `session.id` synchronously instead of
		// falling back to a separate id.
		try {
			return this._earlyClient?.getSessionInfo() ?? {}
		} catch {
			return {}
		}
	}

	setPreferredSessionId(sessionId: string): void {
		// Applied synchronously to the (early) client rather than buffered:
		// buffering would defer it until after init completes, which is too late
		// to influence the session id resolved during init (and baked into the
		// OTel resource). The early client is captured in _init, before async
		// init finishes, so this lands in time.
		try {
			this._earlyClient?.setPreferredSessionId(sessionId)
		} catch {
			// ignore — session id adoption is best-effort
		}
	}

	stop(): Promise<void> {
		const response = this._bufferCall('stop', [])
		return this._isLoaded ? response : Promise.resolve()
	}

	isInitialized(): boolean {
		const response = this._bufferCall('getIsInitialized', [])
		return this._isLoaded ? response : false
	}

	_init(client: ObservabilityClient): void {
		this._earlyClient = client
		const checkInitialized = () => {
			if (client.getIsInitialized()) {
				this.load(client)
			} else {
				setTimeout(checkInitialized, 100)
			}
		}

		checkInitialized()
	}
}

// The _LDObserve object is for internal use.
const _LDObserve = new LDObserveClass()

// The LDObserve object is for external use and is exposed with a limited interface.
const LDObserve: Observe = _LDObserve

export { LDObserve, _LDObserve }
