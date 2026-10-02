import type { NodeOptions } from '@launchdarkly/observability-node'

/**
 * Configuration for the server-side LaunchDarkly observability plugin in
 * Next.js. Extends the node {@link NodeOptions} with the LaunchDarkly SDK key
 * required to initialize the plugin in standalone mode.
 */
export interface ObservabilityEnv extends NodeOptions {
	/** The LaunchDarkly SDK key for the environment to send telemetry to. */
	sdkKey: string
}
