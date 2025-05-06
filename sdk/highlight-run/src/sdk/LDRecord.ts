import type { Record } from '../api/record'
import type { LDClientMin } from '../client'
import type { StartOptions } from '../client/types/types'
import type { LDPluginEnvironmentMetadata } from '../plugins/plugin'
import { SDKCore } from './LD'

class _LDRecord extends SDKCore implements Record {
	constructor() {
		super()
		void super.load([import('./record').then((m) => m.RecordSDK)])
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

	register(
		client: LDClientMin,
		environmentMetadata: LDPluginEnvironmentMetadata,
	) {
		return this._bufferCall('register', [client, environmentMetadata])
	}
}

export const LDRecord = new _LDRecord()
