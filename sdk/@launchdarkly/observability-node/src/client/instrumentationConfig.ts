import type { InstrumentationConfigMap } from '@opentelemetry/auto-instrumentations-node'
import type { Instrumentation } from '@opentelemetry/instrumentation'

/**
 * Applies user-provided instrumentation config to instrumentations that were
 * already created and registered when the module was loaded.
 *
 * Instrumentations are created at module load so that they can patch modules
 * required after this package, before the plugin (and its options) exist. The
 * config is applied here with `setConfig`, merged over each instrumentation's
 * current config so that the plugin's defaults are kept unless overridden.
 *
 * @returns the instrumentations that remain enabled, which should be the ones
 * handed to the OpenTelemetry `NodeSDK` (it re-enables every instrumentation
 * it is given).
 */
export function applyInstrumentationConfig(
	instrumentations: Instrumentation[],
	configMap: InstrumentationConfigMap | undefined,
	warn: (message: string) => void = console.warn,
): Instrumentation[] {
	if (!configMap) {
		return instrumentations
	}
	const disabled = new Set<string>()
	for (const [name, config] of Object.entries(configMap)) {
		if (!config) continue
		const instrumentation = instrumentations.find(
			(i) => i.instrumentationName === name,
		)
		if (!instrumentation) {
			if (config.enabled !== false) {
				warn(
					`@launchdarkly/observability-node: instrumentationConfig for '${name}' was ignored because that instrumentation is not registered (it is unknown, disabled by default, or disabled by OTEL_NODE_ENABLED_INSTRUMENTATIONS / OTEL_NODE_DISABLED_INSTRUMENTATIONS).`,
				)
			}
			continue
		}
		if (config.enabled === false) {
			instrumentation.disable()
			disabled.add(name)
			continue
		}
		instrumentation.setConfig({
			...instrumentation.getConfig(),
			...config,
			enabled: true,
		})
	}
	return instrumentations.filter((i) => !disabled.has(i.instrumentationName))
}
