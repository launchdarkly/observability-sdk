// Minimal NestJS application used by `src/integration/nestjs.test.ts`.
//
// The plugin is loaded before `@nestjs/core` so that its bundled
// OpenTelemetry instrumentations are registered when NestJS is required, as a
// CommonJS application would do it. Spans are exported to the OTLP endpoint
// given in `OTLP_ENDPOINT`. Set `PLUGIN_PATH` to load the plugin from a
// different location (defaults to this package's `dist` build).
const path = require('node:path')

const pluginPath =
	process.env.PLUGIN_PATH ??
	path.join(__dirname, '..', '..', 'dist', 'index.cjs')
const { Observability, LDObserve } = require(pluginPath)
const { init } = require('@launchdarkly/node-server-sdk')

const ldClient = init('sdk-key-nestjs-test', {
	offline: true,
	plugins: [
		new Observability({
			serviceName: 'nestjs-test',
			otlpEndpoint: process.env.OTLP_ENDPOINT,
			backendUrl: process.env.BACKEND_URL,
			disableConsoleRecording: true,
		}),
	],
})

require('reflect-metadata')
const { Controller, Get, Module } = require('@nestjs/common')
const { NestFactory } = require('@nestjs/core')

class AppController {
	getHello() {
		return 'Hello World!'
	}
}
Get()(
	AppController.prototype,
	'getHello',
	Object.getOwnPropertyDescriptor(AppController.prototype, 'getHello'),
)
Controller()(AppController)

class AppModule {}
Module({ controllers: [AppController] })(AppModule)

async function main() {
	const app = await NestFactory.create(AppModule, { logger: false })
	await app.listen(0, '127.0.0.1')
	const res = await fetch(`${await app.getUrl()}/`)
	const body = await res.text()
	if (body !== 'Hello World!') {
		throw new Error(`unexpected response: ${res.status} ${body}`)
	}
	await app.close()
	await LDObserve.flush()
	ldClient.close()
}

main().then(
	() => process.exit(0),
	(err) => {
		console.error(err)
		process.exit(1)
	},
)
