import type { LDClientMin } from '../integrations/launchdarkly/types/LDClient'
import type { Hook } from '../integrations/launchdarkly'

export interface LDPlugin {
	getMetadata(): LDPluginMetadata
	register(
		client: LDClientMin,
		environmentMetadata: LDPluginEnvironmentMetadata,
	): void
	getHooks?(metadata: LDPluginEnvironmentMetadata): Hook[]
}

export interface LDPluginMetadata {
	readonly name: string
}

export interface LDPluginSdkMetadata {
	name: string
	version: string
	wrapperName?: string
	wrapperVersion?: string
}

export interface LDPluginApplicationMetadata {
	id?: string
	version?: string
}

export interface LDPluginEnvironmentMetadata {
	sdk: LDPluginSdkMetadata
	application?: LDPluginApplicationMetadata
	clientSideId: string
}
