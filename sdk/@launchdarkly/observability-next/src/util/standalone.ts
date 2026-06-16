import { SDK_NAME, SDK_VERSION } from './version'

/**
 * Application metadata that LaunchDarkly associates with telemetry. Mirrors the
 * `application` field of the LaunchDarkly client configuration so that data sent
 * in standalone mode is tagged identically to data sent through an LD client.
 */
export interface LDApplicationInfo {
	id?: string
	version?: string
}

/**
 * The LaunchDarkly observability and session replay plugins are normally
 * registered with a LaunchDarkly feature-flag client, which hands them an
 * `LDPluginEnvironmentMetadata` object describing the environment (and crucially
 * the SDK credential) during `getHooks`. There is no LaunchDarkly feature-flag
 * SDK for Next.js, so this SDK runs the plugins in "standalone" mode: we build
 * the same metadata object ourselves from the LaunchDarkly SDK key and invoke
 * the plugin's `getHooks`, which performs the one-time initialization that loads
 * the `LDObserve` / `LDRecord` singletons.
 *
 * The hooks returned by `getHooks` are only used for feature-flag evaluation and
 * identify tracking, which cannot fire without an LD client, so they are
 * discarded in standalone mode.
 */
export function standaloneMetadata(
	sdkKey: string,
	application?: LDApplicationInfo,
) {
	return {
		sdk: {
			name: SDK_NAME,
			version: SDK_VERSION,
		},
		sdkKey,
		clientSideId: sdkKey,
		application,
	}
}

/**
 * A minimal structural type for a LaunchDarkly plugin so we can trigger
 * standalone initialization without depending on a specific LD client SDK
 * package. Both the browser plugins (`@launchdarkly/observability`,
 * `@launchdarkly/session-replay`) and the node plugin
 * (`@launchdarkly/observability-node`) satisfy this shape.
 */
export interface StandalonePlugin {
	getHooks?(metadata: ReturnType<typeof standaloneMetadata>): unknown
	register?(
		client: unknown,
		metadata: ReturnType<typeof standaloneMetadata>,
	): void
}

interface InitRegistry {
	[key: string]: boolean
}
declare var globalThis: { __ldObservabilityNext?: InitRegistry }

/**
 * Initialize a browser plugin in standalone mode exactly once. The guard is
 * keyed on `globalThis` (where the LDObserve / LDRecord singletons also live) so
 * that initialization is idempotent across this SDK's separate client and ssr
 * bundles — e.g. when both `LDObservabilityInit` and a Pages Router `_error`
 * page would otherwise init the same plugin.
 */
export function ensureStandaloneInit(
	key: string,
	createPlugin: () => StandalonePlugin,
	sdkKey: string,
	application?: LDApplicationInfo,
) {
	const registry = (globalThis.__ldObservabilityNext ??= {})
	if (registry[key]) {
		return
	}
	registry[key] = true

	const plugin = createPlugin()
	plugin.getHooks?.(standaloneMetadata(sdkKey, application))
}
