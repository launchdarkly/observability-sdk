import type { Record } from '../api/record'
import type { SessionDetails } from '../client'
import type { LDClient } from '../integrations/launchdarkly'
import type { StartOptions } from '../client/types/types'
import type { LDPluginEnvironmentMetadata } from '../plugins/plugin'
import { BufferedClass } from './buffer'

class _LDRecord extends BufferedClass<Record> implements Record {
	getSession(): SessionDetails | null {
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
		client: LDClient,
		environmentMetadata: LDPluginEnvironmentMetadata,
	) {
		return this._bufferCall('register', [client, environmentMetadata])
	}
}

interface GlobalThis {
	LDRecord?: _LDRecord
}
declare var globalThis: GlobalThis

export let LDRecord!: _LDRecord
if (typeof globalThis !== 'undefined') {
	if (globalThis.LDRecord) {
		LDRecord = globalThis.LDRecord
	} else {
		LDRecord = new _LDRecord()
		globalThis.LDRecord = LDRecord
	}
} else {
	LDRecord = new _LDRecord()
}
