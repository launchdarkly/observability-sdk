import { Attributes, Span as OtelSpan, SpanOptions } from '@opentelemetry/api'
import { ObservabilityClient } from '../client/ObservabilityClient'
import { Metric } from '../api/Metric'
import { RequestContext } from '../api/RequestContext'
import { Observe } from '../api/Observe'
import { BufferedClass } from './BufferedClass'

class LDObserveClass
	extends BufferedClass<ObservabilityClient>
	implements Observe
{
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
		return this._bufferCall('log', [message, level, attributes])
	}

	parseHeaders(headers: Record<string, string>): RequestContext {
		return (
			this._bufferCall('parseHeaders', [headers]) || {
				sessionId: headers['x-session-id'],
				requestId: headers['x-request-id'],
				userId: headers['x-user-id'],
			}
		)
	}

	runWithHeaders(
		name: string,
		headers: Record<string, string>,
		cb: (span: OtelSpan) => any,
		options?: SpanOptions,
	): any {
		return (
			this._bufferCall('runWithHeaders', [name, headers, cb, options]) ||
			cb({} as OtelSpan)
		)
	}

	startWithHeaders(
		spanName: string,
		headers: Record<string, string>,
		options?: SpanOptions,
	): OtelSpan {
		return (
			this._bufferCall('startWithHeaders', [
				spanName,
				headers,
				options,
			]) || ({} as OtelSpan)
		)
	}

	startSpan(spanName: string, options?: SpanOptions): OtelSpan {
		return (
			this._bufferCall('startSpan', [spanName, options]) ||
			({} as OtelSpan)
		)
	}

	startActiveSpan<T>(
		spanName: string,
		fn: (span: OtelSpan) => T,
		options?: SpanOptions,
	): T {
		return (
			this._bufferCall('startActiveSpan', [spanName, fn, options]) ||
			fn({} as OtelSpan)
		)
	}

	setUserId(userId: string): Promise<void> {
		return this._bufferCall('setUserId', [userId]) || Promise.resolve()
	}

	getSessionInfo(): any {
		return this._bufferCall('getSessionInfo', []) || {}
	}

	stop(): Promise<void> {
		return this._bufferCall('stop', []) || Promise.resolve()
	}

	isInitialized(): boolean {
		return this._bufferCall('getIsInitialized', []) || false
	}

	// Internal method to initialize with ObservabilityClient
	_init(client: ObservabilityClient): void {
		// Wait for the client to be fully initialized before loading
		const checkInitialized = () => {
			if (client.getIsInitialized()) {
				this.load(client)
			} else {
				// Check again in a short interval
				setTimeout(checkInitialized, 100)
			}
		}

		// Start checking for initialization
		checkInitialized()
	}
}

// The _LDObserve object is for internal use.
const _LDObserve = new LDObserveClass()

// The LDObserve object is for external use and is exposed with a limited interface.
const LDObserve: Observe = _LDObserve

export { LDObserve, _LDObserve }
