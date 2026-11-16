import { spawn } from 'node:child_process'
import { existsSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { beforeAll, describe, expect, it } from 'vitest'
import { type ScopedSpan, startCollector } from '../testing/collector.js'

// Boots a minimal NestJS 12 application (test/nestjs) with the built plugin
// (dist/index.cjs, so run `yarn build` first), sends one request and checks
// which spans reach an OTLP collector.
const APP_DIR = fileURLToPath(new URL('../../test/nestjs/', import.meta.url))
const PLUGIN = fileURLToPath(new URL('../../dist/index.cjs', import.meta.url))

async function runApp(file: string) {
	if (!existsSync(PLUGIN)) {
		throw new Error(`${PLUGIN} not found, run \`yarn build\` first`)
	}
	const collector = await startCollector()
	const child = spawn(process.execPath, [file], {
		cwd: APP_DIR,
		env: {
			...process.env,
			OTLP_ENDPOINT: collector.url,
			BACKEND_URL: collector.url,
		},
		stdio: ['ignore', 'pipe', 'pipe'],
	})
	let output = ''
	child.stdout.on('data', (chunk) => (output += chunk))
	child.stderr.on('data', (chunk) => (output += chunk))
	const code = await new Promise<number | null>((resolve) =>
		child.on('exit', resolve),
	)
	await collector.close()
	return { code, output, spans: collector.spans() }
}

describe.each(['app.cjs', 'app.mjs'])('NestJS 12 (%s)', (file) => {
	let result: { code: number | null; output: string; spans: ScopedSpan[] }

	beforeAll(async () => {
		result = await runApp(file)
	}, 60_000)

	it('starts, serves a request and exports HTTP spans', () => {
		expect(result.code, result.output).toBe(0)
		expect(
			result.spans.some(
				(s) => s.scope === '@opentelemetry/instrumentation-http',
			),
		).toBe(true)
	})

	// Known gap, see #827. instrumentation-nestjs-core supports @nestjs/core
	// <12: NestJS 12 support (open-telemetry/opentelemetry-js-contrib#3733) was
	// reverted in #3836 and is being redone in #3859, which relies on the
	// OpenTelemetry ESM loader hook for both CommonJS and ESM applications.
	// Remove `.fails` once the bundled instrumentation and the plugin's loader
	// support make these spans appear.
	it.fails('exports the NestJS startup and handler spans', () => {
		const names = result.spans.map((s) => s.name)
		expect(names).toContain('Create Nest App')
		expect(names).toContain('AppController.getHello')
	})
})
