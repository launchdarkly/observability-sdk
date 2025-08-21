/**
 * Test file to validate combined bundle size for LaunchDarkly plugins
 * This simulates how a user would import both observability and session-replay
 */

// Import the ultra-minimal versions
import { Observe, Record, ObservabilityPlugin } from './src/sdk/ld-ultra-minimal'

// Export for use in LaunchDarkly SDK
export const createObservabilityPlugin = () => {
	return Observe({
		backendUrl: 'https://api.example.com',
		serviceName: 'my-app',
		enableConsoleRecording: true,
		enablePerformanceRecording: true,
		enableNetworkRecording: true,
	})
}

export const createSessionReplayPlugin = () => {
	return Record({
		backendUrl: 'https://api.example.com',
		privacySetting: 'default',
		recordInteractions: true,
		recordNavigation: true,
		recordErrors: true,
		samplingRate: 1.0,
	})
}

// Combined plugin for both features
export const createCombinedPlugin = () => {
	return ObservabilityPlugin(
		{
			backendUrl: 'https://api.example.com',
			serviceName: 'my-app',
			enableConsoleRecording: true,
			enablePerformanceRecording: true,
		},
		{
			backendUrl: 'https://api.example.com',
			privacySetting: 'default',
			recordInteractions: true,
			recordNavigation: true,
		}
	)
}

// Re-export types
export type { MinimalObserveOptions, MinimalRecordOptions } from './src/sdk/ld-ultra-minimal'

// Default export for convenience
export default {
	createObservabilityPlugin,
	createSessionReplayPlugin,
	createCombinedPlugin,
}