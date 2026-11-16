/**
 * End-to-end checks for `node --import @launchdarkly/observability-node/register`
 * (issue #828): libraries loaded with `import` are instrumented. Each case runs
 * a fixture app from `test/fixtures/esm-instrumentation` in a child process
 * against the built `dist/` output, so `yarn build` must run first (turbo's
 * `test` task depends on `build`).
 *
 * The fixture apps talk to a minimal in-process Postgres wire-protocol server
 * and export spans to an in-process OTLP/HTTP collector, so no database or
 * network access is needed.
 */
import { execFile } from 'node:child_process'
import { existsSync } from 'node:fs'
import http from 'node:http'
import net from 'node:net'
import path from 'node:path'
import { promisify } from 'node:util'
import zlib from 'node:zlib'
import { afterAll, beforeAll, describe, expect, it } from 'vitest'

const execFileAsync = promisify(execFile)

const packageDir = path.resolve(__dirname, '..')
const fixtureDir = path.join(
	packageDir,
	'test',
	'fixtures',
	'esm-instrumentation',
)
const REGISTER = '@launchdarkly/observability-node/register'

type OtlpSpan = {
	name: string
	attributes?: { key: string; value: Record<string, unknown> }[]
}

/** Answers the startup handshake and every simple query with an empty result. */
function startFakePostgres(): Promise<net.Server> {
	const int32 = (n: number) => [
		(n >>> 24) & 0xff,
		(n >>> 16) & 0xff,
		(n >>> 8) & 0xff,
		n & 0xff,
	]
	const readInt32 = (bytes: number[], offset: number) =>
		(bytes[offset] << 24) |
		(bytes[offset + 1] << 16) |
		(bytes[offset + 2] << 8) |
		bytes[offset + 3]
	const ascii = (text: string) => Array.from(text, (c) => c.charCodeAt(0))
	const message = (type: string, body: number[] = []) => [
		type.charCodeAt(0),
		...int32(body.length + 4),
		...body,
	]
	const authenticationOk = message('R', int32(0))
	const readyForQuery = message('Z', ascii('I'))
	const commandComplete = message('C', ascii('SELECT 0\0'))

	const server = net.createServer((socket) => {
		let pending: number[] = []
		let started = false
		socket.on('data', (chunk) => {
			pending.push(...chunk)
			for (;;) {
				if (!started) {
					// StartupMessage has no type byte: Int32 length, then payload.
					if (pending.length < 4) return
					const length = readInt32(pending, 0)
					if (pending.length < length) return
					pending = pending.slice(length)
					started = true
					socket.write(
						Uint8Array.from([
							...authenticationOk,
							...readyForQuery,
						]),
					)
					continue
				}
				if (pending.length < 5) return
				const type = String.fromCharCode(pending[0])
				const length = readInt32(pending, 1)
				if (pending.length < length + 1) return
				pending = pending.slice(length + 1)
				if (type === 'Q') {
					socket.write(
						Uint8Array.from([...commandComplete, ...readyForQuery]),
					)
				} else if (type === 'X') {
					socket.end()
					return
				}
			}
		})
		socket.on('error', () => {})
	})
	return new Promise((resolve) =>
		server.listen(0, '127.0.0.1', () => resolve(server)),
	)
}

/** Collects spans from OTLP/HTTP JSON trace exports; accepts everything else. */
function startCollector(spans: OtlpSpan[]): Promise<http.Server> {
	const server = http.createServer((req, res) => {
		const body: NodeJS.ReadableStream =
			req.headers['content-encoding'] === 'gzip'
				? req.pipe(zlib.createGunzip())
				: req
		let text = ''
		body.setEncoding('utf8')
		body.on('data', (chunk: string) => (text += chunk))
		body.on('end', () => {
			if (req.url === '/v1/traces') {
				const payload = JSON.parse(text)
				for (const rs of payload.resourceSpans ?? []) {
					for (const ss of rs.scopeSpans ?? []) {
						spans.push(...(ss.spans ?? []))
					}
				}
			}
			res.writeHead(200, { 'content-type': 'application/json' })
			res.end('{}')
		})
	})
	return new Promise((resolve) =>
		server.listen(0, '127.0.0.1', () => resolve(server)),
	)
}

const port = (server: net.Server | http.Server) =>
	(server.address() as net.AddressInfo).port

describe('instrumentation of libraries loaded with import', () => {
	let pgServer: net.Server
	let collector: http.Server
	let spans: OtlpSpan[]

	beforeAll(async () => {
		if (!existsSync(path.join(packageDir, 'dist', 'register.mjs'))) {
			throw new Error(
				'dist/register.mjs is missing; run `yarn build` first',
			)
		}
		pgServer = await startFakePostgres()
	})
	afterAll(() => {
		pgServer.close()
	})

	async function runApp(
		app: string,
		nodeArgs: string[],
		env: Record<string, string> = {},
	): Promise<OtlpSpan[]> {
		spans = []
		collector = await startCollector(spans)
		try {
			await execFileAsync(
				process.execPath,
				[...nodeArgs, path.join(fixtureDir, app)],
				{
					// --import resolves its specifier against the cwd, and the
					// package resolves itself by name from inside its own
					// directory.
					cwd: fixtureDir,
					env: {
						...process.env,
						NODE_OPTIONS: '',
						OTLP_ENDPOINT: `http://127.0.0.1:${port(collector)}`,
						PG_PORT: String(port(pgServer)),
						...env,
					},
					timeout: 30_000,
				},
			)
		} finally {
			collector.close()
		}
		return spans
	}

	const pgQuerySpans = (exported: OtlpSpan[]) =>
		exported.filter((s) => s.name.startsWith('pg.query'))

	it('instruments pg required by a CommonJS app without the hook', async () => {
		const exported = await runApp('app.cjs', [], {
			PG_CLIENT: 'require',
		})
		expect(pgQuerySpans(exported)).toHaveLength(1)
	})

	it('instruments pg imported by an ESM-only client in a CommonJS app', async () => {
		const exported = await runApp('app.cjs', ['--import', REGISTER])
		expect(pgQuerySpans(exported)).toHaveLength(1)
	})

	it('instruments pg imported by an ESM-only client in an ES module app', async () => {
		const exported = await runApp('app.mjs', ['--import', REGISTER])
		expect(pgQuerySpans(exported)).toHaveLength(1)
	})

	// Koa's ES module entry is invisible to require-in-the-middle, so these two
	// cases show that the loader hook reaches the SDK's bundled
	// instrumentations (they must share one copy of import-in-the-middle).
	const koaMiddlewareSpans = (exported: OtlpSpan[]) =>
		exported
			.filter((s) =>
				s.attributes?.some(
					(a) =>
						a.key === 'koa.type' &&
						a.value.stringValue === 'middleware',
				),
			)
			.map((s) => s.name)

	it('does not instrument an ES module entry without the hook', async () => {
		const exported = await runApp('koa-app.mjs', [])
		expect(koaMiddlewareSpans(exported)).toEqual([])
	})

	it('instruments an ES module entry with the hook', async () => {
		const exported = await runApp('koa-app.mjs', ['--import', REGISTER])
		expect(koaMiddlewareSpans(exported)).toEqual(['middleware - hello'])
	})
})
