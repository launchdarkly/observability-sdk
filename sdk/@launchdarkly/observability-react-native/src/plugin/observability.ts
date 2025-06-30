import {
	LDClientMin,
	LDPlugin,
	LDPluginMetadata,
	LDPluginEnvironmentMetadata,
} from './plugin'
import { ReactNativeOptions } from '../api/Options'
import { ObservabilityClient } from '../client/ObservabilityClient'
import { _LDObserve } from '../sdk/LDObserve'
import { TracingHook } from '@launchdarkly/node-server-sdk-otel'
import { Hook } from '@launchdarkly/js-server-sdk-common/dist/integrations'

export class Observability implements LDPlugin {
	// TODO: Review source of this hook.
	private readonly _tracingHook: Hook = new TracingHook()
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

	getHooks?(_metadata: LDPluginEnvironmentMetadata): Hook[] {
		return [this._tracingHook]
	}
}
