import type { Record } from '../api/record'
import type { SessionDetails } from '../client'
import type { LDClient } from '../integrations/launchdarkly'
import type { StartOptions } from '../client/types/types'
import type { LDPluginEnvironmentMetadata } from '../plugins/plugin'
import { BufferedClass } from './buffer'

class _LDRecord extends BufferedClass<Record> implements Record {
	private _deferredStartOptions?: StartOptions

	getSession(): SessionDetails | null {
		return this._isLoaded ? this._callSdk('getSession', []) : null
	}

	protected async _startSdk() {
		await this._sdk.start(this._deferredStartOptions)
		this._deferredStartOptions = undefined
	}

	// Never buffered: with `manualStart`, `start()` may run before the plugin
	// has attached an SDK. It then stays pending and resolves once `load`
	// attaches and starts the SDK.
	async start(options?: StartOptions) {
		this._deferredStartOptions = options
		return this._start()
	}

	// Never buffered, and never throws: before an SDK is attached it just
	// cancels a start that has not run yet.
	stop() {
		void this._stop(() => this._sdk.stop())
	}

	addSessionProperties(properties: { [key: string]: any }) {
		return this._bufferCall('addSessionProperties', [properties])
	}

	getRecordingState() {
		return this._isLoaded
			? this._callSdk('getRecordingState', [])
			: ('NotRecording' as const)
	}

	snapshot(element: HTMLCanvasElement) {
		return this._isLoaded
			? this._callSdk('snapshot', [element])
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
