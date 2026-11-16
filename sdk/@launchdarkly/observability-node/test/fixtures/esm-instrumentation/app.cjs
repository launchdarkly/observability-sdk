// CommonJS application. Its database client is an ES module, unless
// PG_CLIENT=require selects the CommonJS client.
const { init } = require('@launchdarkly/node-server-sdk')
const { LDObserve, Observability } = require('@launchdarkly/observability-node')

async function main() {
	const client = init('test-sdk-key', {
		offline: true,
		plugins: [
			new Observability({
				serviceName: 'esm-instrumentation-cjs',
				otlpEndpoint: process.env.OTLP_ENDPOINT,
				backendUrl: process.env.OTLP_ENDPOINT,
				disableConsoleRecording: true,
			}),
		],
	})
	const { runQuery } =
		process.env.PG_CLIENT === 'require'
			? require('./cjs-client.cjs')
			: await import('./esm-only-client.mjs')
	await runQuery()
	await LDObserve.flush()
	client.close()
}

main().then(
	() => process.exit(0),
	(err) => {
		console.error(err)
		process.exit(1)
	},
)
