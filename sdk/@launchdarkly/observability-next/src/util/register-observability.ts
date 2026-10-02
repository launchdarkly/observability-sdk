import { isNodeJsRuntime } from './is-node-js-runtime'
import { standaloneMetadata, type StandalonePlugin } from './standalone'
import type { ObservabilityEnv } from './types'

let initPromise: Promise<void> | undefined

async function init(env: ObservabilityEnv) {
	// Import lazily so the OpenTelemetry/node dependencies are never pulled into
	// an edge bundle.
	const { Observability } = await import('@launchdarkly/observability-node')

	const { sdkKey, ...options } = env
	const plugin = new Observability(options) as unknown as StandalonePlugin
	// `register` ignores the client argument and initializes LDObserve from the
	// SDK key in the metadata. See util/standalone.ts.
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
 */
export async function registerObservability(env: ObservabilityEnv) {
	if (!isNodeJsRuntime()) {
		console.info(
			`LaunchDarkly observability not registered: NEXT_RUNTIME=${process.env.NEXT_RUNTIME}`,
		)
		return
	}

	// Cache the in-flight/successful init, but drop the cached promise if it
	// rejects so a failed import or registration can be retried on a later
	// call rather than poisoning every subsequent request.
	initPromise ??= init(env).catch((err) => {
		initPromise = undefined
		throw err
	})
	return initPromise
}
