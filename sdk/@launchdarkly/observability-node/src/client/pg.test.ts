import net from 'node:net'
import { createRequire } from 'node:module'
import { afterAll, beforeAll, describe, expect, it } from 'vitest'
import {
	type Collector,
	type OtlpSpan,
	startCollector,
} from '../testing/collector.js'
import { ObservabilityClient } from './ObservabilityClient.js'
import { PG_INSTRUMENTATION_NAME, PG_REDACTED_VALUE } from './pg.js'

// Values that must never leave the process when
// `disablePgInstrumentationAttributes` is set.
const SECRET_DATABASE = 'secret_db_8f3a'
const SECRET_USER = 'secret_user_8f3a'
const SECRET_TABLE = 'secret_customers_8f3a'
const SECRET_VALUE = 'hunter2_8f3a'
const FAILING_TABLE = 'secret_missing_8f3a'

// A minimal PostgreSQL server: accepts any startup message, answers simple and
// extended-protocol queries with an empty result, and fails any query that
// mentions FAILING_TABLE. Protocol bytes are handled as latin1 strings.
function startFakePostgres(): Promise<net.Server> {
	const int32 = (n: number) =>
		String.fromCharCode(
			(n >>> 24) & 255,
			(n >>> 16) & 255,
			(n >>> 8) & 255,
			n & 255,
		)
	const readInt32 = (s: string, offset: number) =>
		((s.charCodeAt(offset) << 24) |
			(s.charCodeAt(offset + 1) << 16) |
			(s.charCodeAt(offset + 2) << 8) |
			s.charCodeAt(offset + 3)) >>>
		0
	const message = (type: string, body = '') =>
		`${type}${int32(body.length + 4)}${body}`
	const ready = message('Z', 'I')
	const complete = message('C', 'SELECT 0\0')
	const error = message('E', 'SERROR\0C42P01\0Mrelation does not exist\0\0')

	const server = net.createServer((socket) => {
		let buffer = ''
		let started = false
		let failed = false
		const send = (data: string) => socket.write(data, 'latin1')
		socket.setEncoding('latin1')
		socket.on('error', () => {})
		socket.on('data', (chunk: string) => {
			buffer += chunk
			for (;;) {
				if (!started) {
					if (buffer.length < 8) return
					const length = readInt32(buffer, 0)
					if (buffer.length < length) return
					const code = readInt32(buffer, 4)
					buffer = buffer.slice(length)
					if (code === 80877103) {
						// SSLRequest: refuse, the client continues in plain text.
						send('N')
						continue
					}
					started = true
					send(message('R', int32(0)) + ready)
					continue
				}
				if (buffer.length < 5) return
				const type = buffer[0]
				const length = readInt32(buffer, 1)
				if (buffer.length < length + 1) return
				const body = buffer.slice(5, length + 1)
				buffer = buffer.slice(length + 1)
				switch (type) {
					case 'Q':
						send(
							(body.includes(FAILING_TABLE) ? error : complete) +
								ready,
						)
						break
					case 'P':
						failed = body.includes(FAILING_TABLE)
						send(failed ? error : message('1'))
						break
					case 'B':
						if (!failed) send(message('2'))
						break
					case 'D':
						if (!failed) send(message('n'))
						break
					case 'E':
						if (!failed) send(complete)
						break
					case 'S':
						failed = false
						send(ready)
						break
					case 'X':
						socket.end()
						return
				}
			}
		})
	})
	return new Promise((resolve) =>
		server.listen(0, '127.0.0.1', () => resolve(server)),
	)
}

const portOf = (server: net.Server) =>
	(server.address() as net.AddressInfo).port

const attribute = (span: OtlpSpan, key: string) =>
	span.attributes?.find((a) => a.key === key)?.value

describe('disablePgInstrumentationAttributes', () => {
	let postgres: net.Server
	let collector: Collector
	let client: ObservabilityClient
	let pgSpans: OtlpSpan[] = []
	const received = () => collector.received

	beforeAll(async () => {
		postgres = await startFakePostgres()
		collector = await startCollector()
		client = new ObservabilityClient('pg-redaction-test', {
			otlpEndpoint: collector.url,
			backendUrl: collector.url,
			disableConsoleRecording: true,
			disablePgInstrumentationAttributes: true,
		})

		// Load pg after the client so the instrumentation patches it.
		const pg = createRequire(import.meta.url)('pg')
		const connection = {
			host: 'localhost',
			port: portOf(postgres),
			database: SECRET_DATABASE,
			user: SECRET_USER,
			password: 'unused',
		}

		const pgClient = new pg.Client(connection)
		await pgClient.connect()
		await pgClient.query(`SELECT * FROM ${SECRET_TABLE}`)
		await pgClient.query(`SELECT * FROM ${SECRET_TABLE} WHERE name = $1`, [
			SECRET_VALUE,
		])
		await expect(
			pgClient.query(`SELECT * FROM ${FAILING_TABLE} WHERE name = $1`, [
				SECRET_VALUE,
			]),
		).rejects.toThrow('relation does not exist')
		await pgClient.end()

		const pool = new pg.Pool(connection)
		await pool.query(`SELECT * FROM ${SECRET_TABLE}`)
		await pool.end()

		await client.flush()

		pgSpans = collector
			.spans()
			.filter((span) => span.scope === PG_INSTRUMENTATION_NAME)
	}, 30_000)

	afterAll(async () => {
		await client?.stop()
		postgres?.close()
		await collector?.close()
	})

	it('exports pg spans', () => {
		const names = pgSpans.map((s) => s.name)
		expect(names.filter((n) => n.startsWith('pg.query')).length).toBe(4)
		expect(names).toContain('pg.connect')
		expect(names).toContain('pg-pool.connect')
	})

	it('does not export query text, values or connection details', () => {
		const exported = [
			...(received().get('/v1/traces') ?? []),
			...(received().get('/v1/metrics') ?? []),
			...(received().get('/v1/logs') ?? []),
		].join('\n')
		expect(exported.length).toBeGreaterThan(0)
		for (const secret of [
			SECRET_DATABASE,
			SECRET_USER,
			SECRET_TABLE,
			SECRET_VALUE,
			FAILING_TABLE,
		]) {
			expect(exported).not.toContain(secret)
		}
	})

	it('redacts stable and connection attributes on every pg span', () => {
		const port = portOf(postgres)
		for (const span of pgSpans) {
			expect(span.name).not.toContain(SECRET_DATABASE)
			for (const key of [
				'db.namespace',
				'server.address',
				'server.port',
			]) {
				expect(attribute(span, key), `${span.name} ${key}`).toEqual({
					stringValue: PG_REDACTED_VALUE,
				})
			}
			expect(JSON.stringify(span)).not.toContain(`${port}`)
		}
		for (const span of pgSpans.filter((s) =>
			s.name.startsWith('pg.query'),
		)) {
			expect(attribute(span, 'db.query.text')).toEqual({
				stringValue: PG_REDACTED_VALUE,
			})
			expect(attribute(span, 'db.postgresql.values')).toBeUndefined()
		}
	})

	it('drops connection details from pg metrics', () => {
		const metrics = (received().get('/v1/metrics') ?? []).join('\n')
		expect(metrics).toContain('db.client.operation.duration')
		expect(metrics).not.toContain('"db.namespace"')
		expect(metrics).not.toContain('"server.address"')
		expect(metrics).not.toContain('"db.client.connection.pool.name"')
	})
})
