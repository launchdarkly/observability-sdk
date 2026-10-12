#!/usr/bin/env node
// Local OTLP capture receiver: records POST bodies for /v1/{traces,metrics,logs}
// to /tmp/otlp-capture so the e2e app can be pointed at it with
// LAUNCHDARKLY_OTEL_ENDPOINT=http://127.0.0.1:4318.

import { writeFileSync, mkdirSync } from 'node:fs'

mkdirSync('/tmp/otlp-capture', { recursive: true })

import { createServer } from 'node:http'

let seq = 0
const http = createServer((req, res) => {
	const chunks = []
	req.on('data', (c) => chunks.push(c))
	req.on('end', () => {
		const body = Buffer.concat(chunks)
		const file = `/tmp/otlp-capture/${seq++}-${req.url.replace(/\//g, '_')}.bin`
		writeFileSync(file, body)
		console.log(
			`captured ${req.method} ${req.url} bytes=${body.length} -> ${file}`,
		)
		res.writeHead(200, { 'content-type': 'application/x-protobuf' })
		res.end(Buffer.alloc(0))
	})
	res.on('error', () => {})
})

process.on('SIGTERM', () => process.exit(0))
http.listen(process.env.CAPTURE_PORT || 4318, '127.0.0.1', () =>
	console.log(
		'otlp capture receiver on 127.0.0.1:' +
			(process.env.CAPTURE_PORT || 4318),
	),
)
