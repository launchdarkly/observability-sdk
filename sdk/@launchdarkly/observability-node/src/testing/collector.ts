import http from 'node:http'
import net from 'node:net'
import zlib from 'node:zlib'

export type OtlpAttribute = { key: string; value: Record<string, unknown> }
export type OtlpSpan = { name: string; attributes?: OtlpAttribute[] }
export type ScopedSpan = OtlpSpan & { scope?: string }

export type Collector = {
	url: string
	/** Raw (decompressed) request bodies, keyed by request path. */
	received: Map<string, string[]>
	/** Spans from every `/v1/traces` request, with their scope name. */
	spans(): ScopedSpan[]
	close(): Promise<void>
}

/**
 * Starts a minimal OTLP/HTTP JSON collector on localhost. Any request outside
 * `/v1/` (the sampling config GraphQL query) gets an empty sampling config.
 */
export async function startCollector(): Promise<Collector> {
	const received = new Map<string, string[]>()
	const server = http.createServer((req, res) => {
		const body =
			req.headers['content-encoding'] === 'gzip'
				? req.pipe(zlib.createGunzip())
				: req
		let text = ''
		body.setEncoding('utf8')
		body.on('data', (chunk: string) => (text += chunk))
		body.on('end', () => {
			const path = req.url ?? ''
			received.set(path, [...(received.get(path) ?? []), text])
			res.setHeader('content-type', 'application/json')
			res.end(
				path.startsWith('/v1/') ? '{}' : '{"data":{"sampling":null}}',
			)
		})
	})
	await new Promise<void>((resolve) =>
		server.listen(0, '127.0.0.1', () => resolve()),
	)
	const { port } = server.address() as net.AddressInfo
	return {
		url: `http://127.0.0.1:${port}`,
		received,
		spans: () => {
			const spans: ScopedSpan[] = []
			for (const body of received.get('/v1/traces') ?? []) {
				for (const rs of JSON.parse(body).resourceSpans ?? []) {
					for (const ss of rs.scopeSpans ?? []) {
						for (const span of ss.spans ?? []) {
							spans.push({ ...span, scope: ss.scope?.name })
						}
					}
				}
			}
			return spans
		},
		close: () => new Promise((resolve) => server.close(() => resolve())),
	}
}
