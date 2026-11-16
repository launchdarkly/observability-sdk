// ES module application whose database client is an ES module.
//
// The client is imported first, as in a NestJS app where the database module
// is part of the module graph that loads before the LaunchDarkly client is
// set up. Static imports are evaluated in order, so `pg` is loaded before the
// SDK's instrumentations exist unless they were preloaded with `--import`.
import { runQuery } from './esm-only-client.mjs'
import { init } from '@launchdarkly/node-server-sdk'
import { LDObserve, Observability } from '@launchdarkly/observability-node'

const client = init('test-sdk-key', {
	offline: true,
	plugins: [
		new Observability({
			serviceName: 'esm-instrumentation-esm',
			otlpEndpoint: process.env.OTLP_ENDPOINT,
			backendUrl: process.env.OTLP_ENDPOINT,
			disableConsoleRecording: true,
		}),
	],
})

try {
	await runQuery()
	await LDObserve.flush()
	client.close()
	process.exit(0)
} catch (err) {
	console.error(err)
	process.exit(1)
}
