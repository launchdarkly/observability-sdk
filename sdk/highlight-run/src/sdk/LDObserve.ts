import type { Observe } from '../api/observe'
import type { LDClientMin } from '../client'
import type { ErrorMessageType } from '../client/types/shared-types'
import type { OTelMetric as Metric } from '../client/types/types'
import type { Attributes } from '@opentelemetry/api'
import type { LDPluginEnvironmentMetadata } from '../plugins/plugin'
import { BufferedClass } from './buffer'

class _LDObserve extends BufferedClass<Observe> implements Observe {
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

	startSpan() {
		return this._bufferCall('startSpan', [])
	}

	startManualSpan() {
		return this._bufferCall('startManualSpan', [])
	}

	register(
		client: LDClientMin,
		environmentMetadata: LDPluginEnvironmentMetadata,
	) {
		return this._bufferCall('register', [client, environmentMetadata])
	}

	recordLog(message: any, level: string, metadata?: Attributes) {
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
