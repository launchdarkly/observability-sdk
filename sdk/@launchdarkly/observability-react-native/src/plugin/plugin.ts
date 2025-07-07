import {
	Hook,
	LDPluginEnvironmentMetadata,
	LDPluginMetadata,
} from '@launchdarkly/react-native-client-sdk'

// Plugin interface types for React Native LaunchDarkly SDK integration
export interface LDClientMin {
	// Minimal interface for LaunchDarkly client
}

export interface LDPlugin {
	getMetadata(): LDPluginMetadata
	register(
		client: LDClientMin,
		environmentMetadata: LDPluginEnvironmentMetadata,
	): void
	getHooks?(metadata: LDPluginEnvironmentMetadata): Hook[]
}
