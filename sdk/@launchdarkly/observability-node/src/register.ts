/**
 * Preload entry point that instruments libraries loaded with `import`.
 *
 * ```shell
 * node --import @launchdarkly/observability-node/register dist/main.js
 * ```
 *
 * It registers the `import-in-the-middle` loader hook, then loads the SDK so
 * its OpenTelemetry instrumentations are enabled before any application code
 * runs. Configure the `Observability` plugin as usual; this entry point only
 * installs the hooks.
 *
 * Both this file and the SDK bundle resolve `import-in-the-middle` from this
 * package's own dependencies, so the loader hook and the instrumentations
 * share one copy of its module registry.
 */
import * as nodeModule from 'module'
import type * as IITM from 'import-in-the-middle'

const require = nodeModule.createRequire(import.meta.url)

// `module.register` is available from Node.js 18.19 and 20.6.
const register: undefined | ((...args: unknown[]) => void) = (nodeModule as any)
	.register

let waitForHooks: () => Promise<void> = async () => {}
if (typeof register === 'function') {
	const { createAddHookMessageChannel } =
		require('import-in-the-middle') as typeof IITM
	// The loader then only wraps modules that an instrumentation has
	// registered a hook for, rather than every module the application imports.
	const { registerOptions, waitForAllMessagesAcknowledged } =
		createAddHookMessageChannel()
	register('import-in-the-middle/hook.mjs', import.meta.url, registerOptions)
	waitForHooks = waitForAllMessagesAcknowledged
} else {
	console.warn(
		'@launchdarkly/observability-node/register: module.register() is not available in this Node.js version, so libraries loaded with import will not be instrumented. Use Node.js 18.19, 20.6 or later.',
	)
}

// Loading the SDK enables its instrumentations, which registers their hooks.
require('./index.cjs')

// Wait for the loader thread to receive those hooks before the application's
// own imports are resolved.
await waitForHooks()
