import type { Observe } from '../api/observe'
import type { LDClient } from '../integrations/launchdarkly'
import type { ErrorMessageType } from '../client/types/shared-types'
import type { OTelMetric as Metric } from '../client/types/types'
import type { Attributes, Context, Span, SpanOptions } from '@opentelemetry/api'
import type { LDPluginEnvironmentMetadata } from '../plugins/plugin'
import { BufferedClass } from './buffer'
import { getNoopSpan } from '../client/otel/utils'
import { ConsoleMethods } from '../client/types/client'

/**
 * Mirrors the no-tracer path in `ObserveSDK`: hand the callback a noop span so it
 * still runs, whichever argument position it was passed in.
 */
function runWithNoopSpan(options: unknown, context: unknown, fn: unknown) {
	const callback = [fn, context, options].find(
		(candidate) => typeof candidate === 'function',
	) as ((span: Span) => any) | undefined
	return callback?.(getNoopSpan())
}

class _LDObserve extends BufferedClass<Observe> implements Observe {
	start() {
		// avoid buffering the start call
		return this._sdk.start()
	}

	stop() {
		// avoid buffering the stop call
		return this._sdk.stop()
	}

	recordGauge(metric: Metric) {
		return this._bufferCall('recordGauge', [metric])
	}

	recordCount(metric: Metric) {
		return this._bufferCall('recordCount', [metric])
	}

	recordIncr(metric: Omit<Metric, 'value'>) {
		return this._bufferCall('recordIncr', [metric])
	}

	recordHistogram(metric: Metric) {
		return this._bufferCall('recordHistogram', [metric])
	}

	recordUpDownCounter(metric: Metric) {
		return this._bufferCall('recordUpDownCounter', [metric])
	}

	// The callback holds the caller's own work, so it must never be buffered:
	// deferring it would replay that work at an arbitrary later time, or drop it.
	startSpan(
		name: string,
		options: SpanOptions | ((span?: Span) => any),
		context?: Context | ((span?: Span) => any),
		fn?: (span?: Span) => any,
	) {
		if (!this._isLoaded) {
			return runWithNoopSpan(options, context, fn)
		}
		return this._bufferCall('startSpan', [name, options, context, fn])
	}

	startManualSpan(
		name: string,
		options: SpanOptions | ((span: Span) => any),
		context?: Context | ((span: Span) => any),
		fn?: (span: Span) => any,
	) {
		if (!this._isLoaded) {
			return runWithNoopSpan(options, context, fn)
		}
		return this._bufferCall('startManualSpan', [name, options, context, fn])
	}

	register(
		client: LDClient,
		environmentMetadata: LDPluginEnvironmentMetadata,
	) {
		return this._bufferCall('register', [client, environmentMetadata])
	}

	recordLog(message: any, level: ConsoleMethods, metadata?: Attributes) {
		return this._bufferCall('recordLog', [message, level, metadata])
	}

	recordError(
		error: Error,
		message?: string,
		payload?: { [key: string]: string },
		source?: string,
		type?: ErrorMessageType,
	) {
		return this._bufferCall('recordError', [
			error,
			message,
			payload,
			source,
			type,
		])
	}

	setLDContextKeyAttributes(contextKeys: Attributes) {
		return this._bufferCall('setLDContextKeyAttributes', [contextKeys])
	}

	getLDContextKeyAttributes(): Attributes | undefined {
		return this._bufferCall('getLDContextKeyAttributes', [])
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
