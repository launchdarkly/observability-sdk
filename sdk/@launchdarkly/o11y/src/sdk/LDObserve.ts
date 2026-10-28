import type { Observe } from '../api/observe'
import type { LDClient } from '../integrations/launchdarkly'
import type { ErrorMessageType } from '../client/types/shared-types'
import type { OTelMetric as Metric } from '../client/types/types'
import type {
	Attributes,
	AttributeValue,
	Context,
	Span,
	SpanOptions,
} from '@opentelemetry/api'
import type { LDPluginEnvironmentMetadata } from '../plugins/plugin'
import { BufferedClass, type BufferedLoadOptions } from './buffer'
import { getNoopSpan } from '../client/otel/utils'
import { ConsoleMethods } from '../client/types/client'
import { internalLogOnce } from './util'

/**
 * Resolves the span callback from whichever argument position it was passed in,
 * matching the overloads `startSpan` and `startManualSpan` accept.
 */
function resolveSpanCallback(
	options: unknown,
	context: unknown,
	fn: unknown,
): ((span: Span) => any) | undefined {
	if (typeof fn === 'function') {
		return fn as (span: Span) => any
	}
	if (typeof context === 'function') {
		return context as (span: Span) => any
	}
	if (typeof options === 'function') {
		return options as (span: Span) => any
	}
	return undefined
}

// Calls that only update SDK state used to tag telemetry. Buffered ones are
// applied as soon as an SDK is attached, before it starts, so they reach
// telemetry recorded during start-up (e.g. the document load span).
const ATTRIBUTE_METHODS = new Set(['setAttributes', 'removeAttributes'])

class _LDObserve extends BufferedClass<Observe> implements Observe {
	load(sdk: Observe, options?: BufferedLoadOptions) {
		const attributeCalls = this._callBuffer.filter(({ method }) =>
			ATTRIBUTE_METHODS.has(method),
		)
		if (attributeCalls.length) {
			this._callBuffer = this._callBuffer.filter(
				({ method }) => !ATTRIBUTE_METHODS.has(method),
			)
			this._sdk = sdk
			for (const { method, args } of attributeCalls) {
				this._callSdk(method, args)
			}
		}
		super.load(sdk, options)
	}

	protected async _startSdk() {
		await this._sdk.start()
	}

	// Never buffered: with `manualStart`, `start()` may run before the plugin
	// has attached an SDK. It then stays pending and resolves once `load`
	// attaches and starts the SDK.
	async start() {
		return this._start()
	}

	// Never buffered, and never throws: before an SDK is attached it just
	// cancels a start that has not run yet.
	async stop() {
		await this._stop(() => this._sdk.stop())
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
		if (!this._isReady) {
			this._logNoopSpanWarning()
			return resolveSpanCallback(options, context, fn)?.(getNoopSpan())
		}
		return this._bufferCall('startSpan', [name, options, context, fn])
	}

	startManualSpan(
		name: string,
		options: SpanOptions | ((span: Span) => any),
		context?: Context | ((span: Span) => any),
		fn?: (span: Span) => any,
	) {
		if (!this._isReady) {
			this._logNoopSpanWarning()
			return resolveSpanCallback(options, context, fn)?.(getNoopSpan())
		}
		return this._bufferCall('startManualSpan', [name, options, context, fn])
	}

	private _logNoopSpanWarning() {
		internalLogOnce(
			'startSpan/startManualSpan called before start()',
			'span-before-start',
			'warn',
			'The span callback runs on a no-op span and the span is not recorded.',
		)
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

	// Applied directly once an SDK is attached, even before it starts (see
	// ATTRIBUTE_METHODS).
	setAttributes(
		attributes: Record<string, AttributeValue | null | undefined>,
	) {
		if (this._isLoaded) {
			return this._callSdk('setAttributes', [attributes])
		}
		return this._bufferCall('setAttributes', [attributes])
	}

	removeAttributes(keys: string[]) {
		if (this._isLoaded) {
			return this._callSdk('removeAttributes', [keys])
		}
		return this._bufferCall('removeAttributes', [keys])
	}

	getLDContextKeyAttributes(): Attributes | undefined {
		// A getter: read directly once attached rather than buffering.
		return this._isLoaded
			? this._callSdk('getLDContextKeyAttributes', [])
			: undefined
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
