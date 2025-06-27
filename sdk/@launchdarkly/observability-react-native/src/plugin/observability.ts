import {
	LDClientMin,
	LDPlugin,
	LDPluginMetadata,
	LDPluginEnvironmentMetadata,
} from './plugin'
import { ReactNativeOptions } from '../api/Options'
import { ObservabilityClient } from '../client/ObservabilityClient'
import { _LDObserve } from '../sdk/LDObserve'

export class Observability implements LDPlugin {
	constructor(private readonly _options?: ReactNativeOptions) {}

	getMetadata(): LDPluginMetadata {
		return {
			name: '@launchdarkly/observability-react-native',
		}
	}

	register(
		_client: LDClientMin,
		environmentMetadata: LDPluginEnvironmentMetadata,
	): void {
		const sdkKey =
			environmentMetadata.sdkKey || environmentMetadata.mobileKey || ''

		_LDObserve._init(new ObservabilityClient(sdkKey, this._options || {}))
	}
}
