// ES module Koa application. `import Koa from 'koa'` loads Koa's ES module
// entry point, which require-in-the-middle never sees even though the SDK is
// imported first, so Koa spans depend on the import-in-the-middle loader hook.
import { once } from 'node:events'
import { init } from '@launchdarkly/node-server-sdk'
import { LDObserve, Observability } from '@launchdarkly/observability-node'
import Koa from 'koa'

const client = init('test-sdk-key', {
	offline: true,
	plugins: [
		new Observability({
			serviceName: 'esm-instrumentation-koa',
			otlpEndpoint: process.env.OTLP_ENDPOINT,
			backendUrl: process.env.OTLP_ENDPOINT,
			disableConsoleRecording: true,
		}),
	],
})

try {
	const app = new Koa()
	app.use(async function hello(ctx) {
		ctx.body = 'hello'
	})
	const server = app.listen(0, '127.0.0.1')
	await once(server, 'listening')
	const response = await fetch(`http://127.0.0.1:${server.address().port}/`)
	if ((await response.text()) !== 'hello') {
		throw new Error(`unexpected response ${response.status}`)
	}
	server.close()
	await LDObserve.flush()
	client.close()
	process.exit(0)
} catch (err) {
	console.error(err)
	process.exit(1)
}
