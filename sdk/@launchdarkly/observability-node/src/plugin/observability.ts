import {
	LDPluginMetadata,
	LDPluginEnvironmentMetadata,
} from '@launchdarkly/js-server-sdk-common'
import { Hook } from '@launchdarkly/js-server-sdk-common/dist/integrations'
import { LDClientMin, LDPlugin } from './plugin'
import { TracingHook } from '@launchdarkly/node-server-sdk-otel'
import { _LDObserve } from '../sdk/LDObserve'
import { NodeOptions } from '../api/Options'
import { ObservabilityClient } from '../client/ObservabilityClient'

export class Observability implements LDPlugin {
	private readonly _tracingHook: Hook = new TracingHook()
	constructor(private readonly _options?: NodeOptions) {}
	getMetadata(): LDPluginMetadata {
		return {
			name: '@launchdarkly/observability-node',
		}
	}
	register(
		_client: LDClientMin,
		environmentMetadata: LDPluginEnvironmentMetadata,
	): void {
		_LDObserve._init(
			new ObservabilityClient(
				environmentMetadata.sdkKey ?? '',
				this._options ?? {},
			),
		)
	}
	getHooks?(_metadata: LDPluginEnvironmentMetadata): Hook[] {
		return [this._tracingHook]
	}
}
