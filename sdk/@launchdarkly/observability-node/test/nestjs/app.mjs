// ES module variant of `app.cjs`: the same NestJS application, loaded with
// `import` instead of `require`.
import path from 'node:path'
import { fileURLToPath, pathToFileURL } from 'node:url'

const pluginPath =
	process.env.PLUGIN_PATH ??
	path.join(
		path.dirname(fileURLToPath(import.meta.url)),
		'..',
		'..',
		'dist',
		'index.cjs',
	)
const { Observability, LDObserve } = await import(
	pathToFileURL(pluginPath).href
)
const { init } = await import('@launchdarkly/node-server-sdk')

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

await import('reflect-metadata')
const { Controller, Get, Module } = await import('@nestjs/common')
const { NestFactory } = await import('@nestjs/core')

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

try {
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
	process.exit(0)
} catch (err) {
	console.error(err)
	process.exit(1)
}
