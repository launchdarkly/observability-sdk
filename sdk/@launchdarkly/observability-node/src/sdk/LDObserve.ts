import { Attributes, Context, Span, SpanOptions } from "@opentelemetry/api"
import { Metric } from "../api/Metric"
import { Headers, IncomingHttpHeaders, Observe } from "../api/Observe"
import { BufferedClass } from "./buffer"
import type { LDPluginEnvironmentMetadata } from "@launchdarkly/js-server-sdk-common"
import { LDClientMin } from "../plugin/plugin"
import { RequestContext } from "../api/RequestContext"

class _LDObserve extends BufferedClass<Observe> implements Observe {
	recordMetric(metric: Metric): void {
		return this._bufferCall('recordMetric', [metric])
	}
	recordCount(metric: Metric): void {
		return this._bufferCall('recordCount', [metric])
	}
	recordIncr(metric: Omit<Metric, "value">): void {
		return this._bufferCall('recordIncr', [metric])
	}
	recordHistogram(metric: Metric): void {
		return this._bufferCall('recordHistogram', [metric])
	}
	recordUpDownCounter(metric: Metric): void {
		return this._bufferCall('recordUpDownCounter', [metric])
	}
	recordLog(message: any, level: string, secureSessionId?: string | undefined, requestId?: string | undefined, metadata?: Attributes): void {
		return this._bufferCall('recordLog', [message, level, secureSessionId, requestId, metadata])
	}
	recordError(error: Error, secureSessionId: string | undefined, requestId: string | undefined, metadata?: Attributes, options?: { span: Span }): void {
		return this._bufferCall('recordError', [error, secureSessionId, requestId, metadata, options])
	}
	flush(): Promise<void> {
		// TODO: Determine if something more robust is needed here for async operations.
		return this._bufferCall('flush', [])
	}
	setAttributes(attributes: Attributes): void {
		return this._bufferCall('setAttributes', [attributes])
	}
	parseHeaders(headers: Headers | IncomingHttpHeaders): RequestContext {
		return this._bufferCall('parseHeaders', [headers])
	}
	runWithHeaders<T>(name: string, headers: Headers | IncomingHttpHeaders, cb: (span: Span) => T | Promise<T>, options?: SpanOptions): Promise<T> {
		return this._bufferCall('runWithHeaders', [name, headers, cb, options])
	}
	startWithHeaders<T>(spanName: string, headers: Headers | IncomingHttpHeaders, options?: SpanOptions): { span: Span; ctx: Context } {
		return this._bufferCall('startWithHeaders', [spanName, headers, options])
	}
	stop(): Promise<void> {
		return this._bufferCall('stop', [])
	}
	_debug(...data: any[]) {
		this._bufferCall('debug', [...data])
	}
}

interface GlobalThis {
	LDObserve?: _LDObserve
}
declare var globalThis: GlobalThis

export let LDObserve!: _LDObserve
if (typeof globalThis !== 'undefined') {
	if (globalThis.LDObserve) {
		LDObserve = globalThis.LDObserve
	} else {
		LDObserve = new _LDObserve()
		globalThis.LDObserve = LDObserve
	}
} else {
	LDObserve = new _LDObserve()
}
