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
}

export interface LDPluginMetadata {
	name: string
}

export interface LDPluginEnvironmentMetadata {
	sdkKey?: string
	mobileKey?: string
	environment?: string
}
