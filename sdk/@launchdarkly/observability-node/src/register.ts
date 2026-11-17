/**
 * ES module support for the LaunchDarkly Observability Plugin for Node.js.
 *
 * Preload this module with `node --import` so libraries are instrumented even
 * when they are loaded with `import` (ES modules), or are imported before the
 * module that initializes the plugin is evaluated:
 *
 * ```shell
 * node --import @launchdarkly/observability-node/register dist/main.js
 * ```
 *
 * It follows OpenTelemetry's ES module support: it registers the
 * `import-in-the-middle` loader hook (the hook that
 * `@opentelemetry/instrumentation/hook.mjs` re-exports) with `module.register`,
 * then enables the plugin's instrumentations before the application loads.
 * Initialize the plugin in the application as usual; this module only installs
 * the hooks.
 *
 * @packageDocumentation
 */
import * as nodeModule from 'node:module'

// `module.register` is available from Node.js 18.19 / 20.6 (and is missing
// from the @types/node version this package builds against).
type Register = (specifier: string, parentURL: string) => void
const register = (nodeModule as unknown as { register?: Register }).register

if (typeof register === 'function') {
	// Resolved relative to this file, so the loader hook uses the same copy of
	// import-in-the-middle as the plugin's instrumentations: the hook only
	// reaches the `Hook`s created by that copy.
	register('import-in-the-middle/hook.mjs', import.meta.url)
} else {
	console.warn(
		'@launchdarkly/observability-node/register: module.register is not available in this version of Node.js (it requires 18.19, 20.6 or later), so libraries loaded with import will not be instrumented.',
	)
}

// Loading the plugin creates and enables its instrumentations, so that they
// also patch libraries the application imports before it initializes the
// plugin.
// @ts-ignore: resolved against the built output in dist/
await import('./index.cjs')
