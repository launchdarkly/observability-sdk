/**
 * End-to-end validation for `_LDObserve.track(...)` on the React Native SDK.
 *
 * Runs against a real observability backend OTLP intake at
 *   http://localhost:9096/otel/v1/traces
 * (SSH-tunneled to the devbox) and verifies the `launchdarkly.track` span
 * lands in the devbox ClickHouse `default.traces` table with the expected
 * attributes.
 *
 * Why this test bypasses the SDK's own OTLP exporter:
 * - The SDK ships `@opentelemetry/exporter-trace-otlp-http`, which serializes
 *   to OTLP/JSON (`Content-Type: application/json`).
 * - The devbox backend handler at `/otel/v1/traces` calls
 *   `ptraceotlp.ExportRequest.UnmarshalProto(body)` unconditionally — so
 *   JSON payloads fail with `proto: unexpected end of group`.
 * - To validate the contract-level claim ("track produces a
 *   `launchdarkly.track` span that lands in CH"), we install a custom
 *   protobuf-emitting exporter and re-register it as the global tracer
 *   provider AFTER `ObservabilityClient` constructs its own. The SDK's
 *   `InstrumentationManager.startSpan` calls
 *   `trace.getTracerProvider().getTracer(...)`, which honors the global,
 *   so this swap is transparent to `_LDObserve.track(...)`.
 *
 * This file is intentionally named `*.e2e-test.ts` so it does NOT match
 * the default `*.test.ts` glob. Run with:
 *   ./node_modules/.bin/vitest run --config vitest.e2e.config.ts
 */
import { describe, it, expect, beforeAll, afterAll } from 'vitest'
import * as http from 'node:http'
import {
	BasicTracerProvider,
	SimpleSpanProcessor,
	ReadableSpan,
	SpanExporter,
} from '@opentelemetry/sdk-trace-base'
import { trace } from '@opentelemetry/api'
import { resourceFromAttributes } from '@opentelemetry/resources'
import { ProtobufTraceSerializer } from '@opentelemetry/otlp-transformer'
import { ExportResult, ExportResultCode } from '@opentelemetry/core'

import { _LDObserve } from './LDObserve'
import { ObservabilityClient } from '../client/ObservabilityClient'
import { LD_TRACK_SPAN_NAME } from '../constants/featureFlags'

const OTLP_TRACES_URL = 'http://localhost:9096/otel/v1/traces'
const CH_BASE = 'http://localhost:8123'

// Numeric project IDs short-circuit in backend/otel/extract.go::projectToInt
// without a DB lookup. "1" is the well-known internal dev project.
const PROJECT_ID = '1'

// Unique per-run so concurrent validation suites don't collide.
const eventKey = `test-event-rn-${Math.random().toString(36).slice(2, 10)}-${Date.now()}`

// ---- Custom protobuf-emitting OTLP exporter ----------------------------------
//
// We bypass `@opentelemetry/exporter-trace-otlp-http` because in both its
// node and browser variants it serializes OTLP/JSON, which the devbox
// backend rejects. Here we use `ProtobufTraceSerializer` directly + a
// minimal Node HTTP POST. This is test-harness code, not SDK code.

class HttpProtobufTraceExporter implements SpanExporter {
	constructor(private readonly url: string) {}

	export(
		spans: ReadableSpan[],
		resultCallback: (result: ExportResult) => void,
	): void {
		const body = ProtobufTraceSerializer.serializeRequest(spans)
		if (!body) {
			resultCallback({ code: ExportResultCode.SUCCESS })
			return
		}
		const u = new URL(this.url)
		const req = http.request(
			{
				method: 'POST',
				hostname: u.hostname,
				port: u.port,
				path: u.pathname,
				headers: {
					'Content-Type': 'application/x-protobuf',
					'Content-Length': String(body.byteLength),
				},
			},
			(res) => {
				const chunks: Buffer[] = []
				res.on('data', (c) => chunks.push(c))
				res.on('end', () => {
					if (
						res.statusCode &&
						res.statusCode >= 200 &&
						res.statusCode < 300
					) {
						resultCallback({ code: ExportResultCode.SUCCESS })
					} else {
						const text = Buffer.concat(chunks).toString('utf8')
						resultCallback({
							code: ExportResultCode.FAILED,
							error: new Error(
								`OTLP HTTP ${res.statusCode}: ${text}`,
							),
						})
					}
				})
			},
		)
		req.on('error', (err) => {
			resultCallback({ code: ExportResultCode.FAILED, error: err })
		})
		req.write(Buffer.from(body))
		req.end()
	}

	shutdown(): Promise<void> {
		return Promise.resolve()
	}

	forceFlush(): Promise<void> {
		return Promise.resolve()
	}
}

// ---- ClickHouse helpers -----------------------------------------------------

function chQuery(sql: string): Promise<string> {
	const url = `${CH_BASE}/?query=${encodeURIComponent(sql)}`
	return fetch(url, { method: 'GET' }).then(async (r) => {
		if (!r.ok) {
			const body = await r.text()
			throw new Error(`ClickHouse HTTP ${r.status}: ${body}`)
		}
		return r.text()
	})
}

async function pollForTrace(timeoutMs = 120_000): Promise<any[]> {
	const deadline = Date.now() + timeoutMs
	const sql = `SELECT SpanName, TraceAttributes, ServiceName, ProjectId, length(TraceAttributes) AS attr_count FROM default.traces WHERE TraceAttributes['key'] = '${eventKey}' AND SpanName = '${LD_TRACK_SPAN_NAME}' LIMIT 5 FORMAT JSONEachRow`
	let lastBody = ''
	while (Date.now() < deadline) {
		const body = await chQuery(sql)
		lastBody = body
		const rows = body
			.trim()
			.split('\n')
			.filter(Boolean)
			.map((l) => JSON.parse(l))
		if (rows.length > 0) {
			return rows
		}
		await new Promise((res) => setTimeout(res, 1_000))
	}
	throw new Error(
		`Timed out waiting for trace with key=${eventKey}; last CH body: ${lastBody}`,
	)
}

// ---- Test -------------------------------------------------------------------

describe('e2e: _LDObserve.track lands in ClickHouse via devbox OTLP', () => {
	let client: ObservabilityClient
	let testProvider: BasicTracerProvider
	let tStart = 0

	beforeAll(async () => {
		// Probe the OTLP intake to confirm the SSH tunnel is alive.
		const probeRes = await fetch(OTLP_TRACES_URL, {
			method: 'POST',
			headers: { 'Content-Type': 'application/x-protobuf' },
			body: new Uint8Array(0),
		}).catch((e) => e as Error)
		if (probeRes instanceof Error) {
			throw new Error(
				`OTLP intake unreachable at ${OTLP_TRACES_URL}: ${probeRes.message}`,
			)
		}
		// Either 200 or 4xx (empty payload). Either proves the route is live.

		_LDObserve._resetForTesting()

		// 1) Construct the SDK. It will install its own WebTracerProvider as
		//    the global tracer provider. We do not stop that; we just replace
		//    it afterwards so the actual track span uses our proto exporter.
		client = new ObservabilityClient(PROJECT_ID, {
			otlpEndpoint: 'http://localhost:9096/otel',
			backendUrl: 'http://localhost:9096/otel',
			serviceName: 'sdk-validation-rn',
			serviceVersion: '0.0.0-e2e',
			disableErrorTracking: true,
			disableLogs: true,
			disableMetrics: true,
		})
		_LDObserve._init(client)

		// 2) Wait for the BufferedClass init loop to flip `_isLoaded`.
		const deadline = Date.now() + 5_000
		while (Date.now() < deadline && !(_LDObserve as any)._isLoaded) {
			await new Promise((res) => setTimeout(res, 50))
		}
		expect((_LDObserve as any)._isLoaded).toBe(true)

		// 3) Replace the global tracer provider with our protobuf-emitting one.
		//    `InstrumentationManager.startSpan` calls
		//    `trace.getTracerProvider().getTracer(...)`, so this swap is
		//    transparent to `_LDObserve.track(...)`.
		testProvider = new BasicTracerProvider({
			resource: resourceFromAttributes({
				'service.name': 'sdk-validation-rn',
				'highlight.project_id': PROJECT_ID,
			}),
			spanProcessors: [
				new SimpleSpanProcessor(
					new HttpProtobufTraceExporter(OTLP_TRACES_URL),
				),
			],
		})
		trace.disable()
		trace.setGlobalTracerProvider(testProvider)
	}, 30_000)

	afterAll(async () => {
		try {
			await testProvider?.shutdown()
		} catch {
			/* ignore */
		}
		try {
			await client?.stop()
		} catch {
			/* ignore */
		}
	})

	it('emits a launchdarkly.track span that lands in default.traces with the expected attributes', async () => {
		tStart = Date.now()
		_LDObserve.track(eventKey, { foo: 'bar' }, 42.0)

		// SimpleSpanProcessor exports synchronously when span.end() is called,
		// but the HTTP POST is async. forceFlush gives us a clear barrier.
		await testProvider.forceFlush()

		// Devbox ingest pipeline: HTTP POST -> backend write_buffer
		// (100ms-5s flush) -> S3 (localstack) -> Kafka -> ClickHouse writer
		// goroutine. Empirically this lands in CH within ~60-120 seconds on
		// the shared devbox.
		const rows = await pollForTrace(180_000)
		const elapsedSec = ((Date.now() - tStart) / 1000).toFixed(2)
		// eslint-disable-next-line no-console
		console.log(
			`[e2e] track -> CH visible in ${elapsedSec}s; row[0]:`,
			JSON.stringify(rows[0]).slice(0, 800),
		)

		expect(rows.length).toBeGreaterThanOrEqual(1)
		const row = rows[0]
		expect(row.SpanName).toBe(LD_TRACK_SPAN_NAME)
		// Required core track attributes
		expect(row.TraceAttributes.key).toBe(eventKey)
		expect(String(row.TraceAttributes.value)).toBe('42')
		expect(row.TraceAttributes.foo).toBe('bar')
		// Project routing arrived from the OTLP resource attribute.
		expect(String(row.ProjectId)).toBe(PROJECT_ID)
	}, 240_000)
})
