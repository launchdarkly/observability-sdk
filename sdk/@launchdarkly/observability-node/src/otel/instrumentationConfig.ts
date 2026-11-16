import { diag } from '@opentelemetry/api'
import type {
	Instrumentation,
	InstrumentationConfig,
} from '@opentelemetry/instrumentation'
import type { InstrumentationConfigMap } from '../api/Options.js'

type AnyFunction = (...args: unknown[]) => unknown
type ConfigRecord = InstrumentationConfig & Record<string, unknown>

/**
 * Chains two hooks: `first` runs, then `second`, which provides the return
 * value. A throw from `first` is logged and does not stop `second`.
 */
function chainHooks(first: AnyFunction, second: AnyFunction): AnyFunction {
	return function (this: unknown, ...args: unknown[]) {
		try {
			first.apply(this, args)
		} catch (e) {
			diag.error('@launchdarkly/observability-node: hook failed', e)
		}
		return second.apply(this, args)
	}
}

/**
 * Merges instrumentation configs left to right. Later plain values replace
 * earlier ones; when both sides set a function for the same key, the two are
 * chained so the earlier hook runs first.
 */
export function mergeInstrumentationConfig(
	...configs: (InstrumentationConfig | undefined)[]
): InstrumentationConfig {
	const merged: ConfigRecord = {}
	for (const config of configs) {
		if (!config) continue
		for (const [key, value] of Object.entries(config)) {
			const existing = merged[key]
			merged[key] =
				typeof existing === 'function' && typeof value === 'function'
					? chainHooks(existing as AnyFunction, value as AnyFunction)
					: value
		}
	}
	return merged
}

/**
 * Snapshot of each instrumentation's config as registered at module load.
 * Every ObservabilityClient merges from this snapshot instead of an
 * instrumentation's current config, so constructing more than one client
 * does not chain hooks repeatedly.
 */
export function snapshotInstrumentationConfig(
	instrumentations: Instrumentation[],
): Map<string, InstrumentationConfig> {
	return new Map(
		instrumentations.map((i) => [
			i.instrumentationName,
			{ ...i.getConfig() },
		]),
	)
}

/**
 * Applies the merged config (load-time snapshot, then SDK overrides, then
 * user config) to each registered instrumentation. Returns the names in
 * `userConfig` that match no registered instrumentation.
 */
export function applyInstrumentationConfig(
	instrumentations: Instrumentation[],
	snapshot: Map<string, InstrumentationConfig>,
	sdkConfig: InstrumentationConfigMap,
	userConfig: InstrumentationConfigMap = {},
): string[] {
	const registered = new Set<string>()
	for (const instrumentation of instrumentations) {
		const name = instrumentation.instrumentationName
		registered.add(name)
		const sdk = sdkConfig[name]
		const user = userConfig[name]
		// With no config for it, this restores the load-time config in case
		// an earlier client changed it.
		instrumentation.setConfig(
			mergeInstrumentationConfig(snapshot.get(name), sdk, user),
		)
		// Bring the patch state in line with the config. Both calls are no-ops
		// when the instrumentation is already in that state.
		if (instrumentation.getConfig().enabled === false) {
			instrumentation.disable()
		} else {
			instrumentation.enable()
		}
	}
	return Object.keys(userConfig).filter((name) => !registered.has(name))
}
