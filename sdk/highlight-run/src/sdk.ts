import type { IntegrationClient } from './integrations'
import type { Client } from './api/client'
import type { HighlightOptions, LDClientMin } from './client'
import type { ErrorMessageType, Source } from './client/types/shared-types'
import type {
	Metadata,
	OTelMetric as Metric,
	StartOptions,
} from './client/types/types'
import { Attributes } from '@opentelemetry/api'
import { LDPluginEnvironmentMetadata } from './plugins/plugin'

class SDKCore implements Client {
	static _instance: SDKCore
	static _integrations: IntegrationClient[] = []

	constructor() {
		if (SDKCore._instance) {
			return SDKCore._instance
		}
		SDKCore._instance = this
		void this.load()
		return this
	}

	private _isLoaded = false
	private _callBuffer: Array<{ method: string; args: any[] }> = []

	private _bufferCall(method: string, args: any[]) {
		if (this._isLoaded) {
			// If already loaded, execute the method directly
			return (this as any)[method](...args)
		} else {
			// Otherwise buffer the call
			this._callBuffer.push({ method, args })
			return undefined
		}
	}

	async load() {
		// Load the modules
		await Promise.all(
			[
				import('./sdk/record').then((m) => m.RecordSDK),
				import('./sdk/observe').then((m) => m.ObserveSDK),
			].map(async (module) => {
				const klass = await module
				const proto = klass.prototype
				for (const key of Reflect.ownKeys(proto)) {
					const desc = Object.getOwnPropertyDescriptor(proto, key)
					if (key === 'constructor' || !desc) {
						continue
					}
					Object.defineProperty(this, key, desc)
				}
			}),
		)

		// Mark as loaded
		this._isLoaded = true

		// Process buffered calls
		for (const { method, args } of this._callBuffer) {
			try {
				;(this as any)[method](...args)
			} catch (error) {
				console.error(
					`Error executing buffered call to ${method}:`,
					error,
				)
			}
		}

		// Clear the buffer
		this._callBuffer = []
	}

	init(projectID?: string | number, debug?: HighlightOptions) {
		return this._bufferCall('init', [projectID, debug])
	}

	identify(identifier: string, metadata?: Metadata, source?: Source) {
		return this._bufferCall('identify', [identifier, metadata, source])
	}

	track(event: string, metadata?: Metadata) {
		return this._bufferCall('track', [event, metadata])
	}

	getSession() {
		return this._isLoaded ? this._bufferCall('getSession', []) : null
	}

	async start(options?: StartOptions) {
		return this._bufferCall('start', [options])
	}

	stop(options?: StartOptions) {
		return this._bufferCall('stop', [options])
	}

	getRecordingState() {
		return this._isLoaded
			? this._bufferCall('getRecordingState', [])
			: ('NotRecording' as const)
	}

	snapshot(element: HTMLCanvasElement) {
		return this._isLoaded
			? this._bufferCall('snapshot', [element])
			: Promise.resolve(undefined)
	}

	error(message: string, payload?: { [key: string]: string }) {
		return this._bufferCall('error', [message, payload])
	}

	metrics(metrics: Metric[]) {
		return this._bufferCall('metrics', [metrics])
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

	consumeError(
		error: Error,
		message?: string,
		payload?: { [key: string]: string },
	) {
		return this._bufferCall('consumeError', [error, message, payload])
	}

	consume(
		error: Error,
		opts: {
			message?: string
			payload?: object
			source?: string
			type?: ErrorMessageType
		},
	) {
		return this._bufferCall('consume', [error, opts])
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

export const LD = new SDKCore()
