import type { Observe } from '../api/observe'
import type { LDClientMin } from '../client'
import type { ErrorMessageType } from '../client/types/shared-types'
import type { OTelMetric as Metric } from '../client/types/types'
import type { Attributes } from '@opentelemetry/api'
import type { LDPluginEnvironmentMetadata } from '../plugins/plugin'
import { SDKCore } from './LD'

class _LDObserve extends SDKCore implements Observe {
	constructor() {
		super()
		void super.load([import('./observe').then((m) => m.ObserveSDK)])
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

export const LDObserve = new _LDObserve()
