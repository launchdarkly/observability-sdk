import type {
	LDPluginEnvironmentMetadata,
	LDPluginMetadata,
} from '@launchdarkly/js-client-sdk'
import type { Hook, LDClient } from '../integrations/launchdarkly'

export type { Hook, LDClient, LDPluginEnvironmentMetadata, LDPluginMetadata }

export interface LDPlugin {
	getMetadata(): LDPluginMetadata
	register(
		client: LDClient,
		environmentMetadata: LDPluginEnvironmentMetadata,
	): void
	getHooks?(metadata: LDPluginEnvironmentMetadata): Hook[]
}
