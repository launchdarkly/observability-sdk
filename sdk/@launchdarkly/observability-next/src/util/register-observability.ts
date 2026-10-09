import { isNodeJsRuntime } from './is-node-js-runtime'
import { standaloneMetadata, type StandalonePlugin } from './standalone'
import type { ObservabilityEnv } from './types'

declare var globalThis: { __ldObservabilityNextRegister?: Promise<void> }

async function init(env: ObservabilityEnv) {
	// Import lazily so the OpenTelemetry/node dependencies are never pulled into
	// an edge bundle.
	const { Observability } = await import('@launchdarkly/observability-node')

	const { sdkKey, ...options } = env
	const plugin = new Observability(options) as unknown as StandalonePlugin
	// `register` ignores the client argument and initializes LDObserve from the
	// SDK key in the metadata. See util/standalone.ts. Inside the node SDK,
	// `Observability.register` is idempotent, so even if a bundler-duplicated
	// copy of this module runs the same call, only the first OpenTelemetry
	// configuration is created.
	plugin.register?.({}, standaloneMetadata(sdkKey))
}

/**
 * Initialize the server-side LaunchDarkly observability plugin in standalone
 * mode. Call this from your Next.js `instrumentation.ts` `register()` hook.
 *
 * Initialization only happens in the Node.js runtime; in the edge runtime this
 * is a no-op (the OpenTelemetry-based node SDK is not edge-compatible). It is
 * idempotent and concurrency-safe, so route-handler wrappers can safely await
 * it on every request.
 *
 * `LDObserve` is a process-global singleton that can only be initialized once,
 * so the first successful call wins and later calls reuse it. All callers
 * (`instrumentation.ts` and any route wrappers) should therefore pass the same
 * `env` — initialize once in `instrumentation.ts` for the canonical config.
 *
 * Bundling note: Turbopack may duplicate this module across server bundles
 * (each entry gets its own `initPromise`), so the in-flight/successful init is
 * additionally cached on `globalThis` — only the first copy constructs the
 * `Observability` plugin (and with it the OTel NodeSDK and instrumentation
 * hooks). A failed init drops the cache so a later call can retry.
 */
export async function registerObservability(env: ObservabilityEnv) {
	if (!isNodeJsRuntime()) {
		console.info(
			`LaunchDarkly observability not registered: NEXT_RUNTIME=${process.env.NEXT_RUNTIME}`,
		)
		return
	}

	try {
		// Cache the init process-wide so duplicated module copies converge on
		// the first initialization instead of each constructing their own
		// plugin (and therefore a second OTel NodeSDK).
		globalThis.__ldObservabilityNextRegister ??= init(env)
	} catch (e) {
		// Synchronous failure scheduling init: drop the cache so a later call
		// can retry, and warn.
		globalThis.__ldObservabilityNextRegister = undefined
		console.warn('LaunchDarkly observability registration failed: ', e)
		return
	}

	try {
		await globalThis.__ldObservabilityNextRegister
	} catch (e) {
		globalThis.__ldObservabilityNextRegister = undefined
		console.warn('LaunchDarkly observability registration failed: ', e)
	}
}
