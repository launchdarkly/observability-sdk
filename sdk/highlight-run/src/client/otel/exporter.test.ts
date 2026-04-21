import {
	afterEach,
	beforeEach,
	describe,
	expect,
	it,
	vi,
	type MockInstance,
} from 'vitest'
import { ExportResultCode } from '@opentelemetry/core'
import {
	OTLPMetricExporterBrowser,
	OTLPTraceExporterBrowserWithXhrRetry,
} from './exporter'
import type { ExportSampler } from './sampling/ExportSampler'

const TRACES_URL = 'https://pub.test.invalid/v1/traces'
const METRICS_URL = 'https://pub.test.invalid/v1/metrics'
const passthroughSampler: ExportSampler = {
	isSamplingEnabled: () => false,
	shouldSample: () => ({ sample: true, attributes: {} }),
	setConfig: () => {},
}

const rejectAllSampler: ExportSampler = {
	isSamplingEnabled: () => true,
	shouldSample: () => ({ sample: false, attributes: {} }),
	setConfig: () => {},
}

describe('OTLPTraceExporterBrowserWithXhrRetry', () => {
	let fetchSpy: MockInstance
	let exporter: OTLPTraceExporterBrowserWithXhrRetry

	beforeEach(() => {
		fetchSpy = vi
			.spyOn(globalThis, 'fetch')
			.mockResolvedValue(new Response('OK', { status: 200 }))
		exporter = new OTLPTraceExporterBrowserWithXhrRetry(
			{ url: TRACES_URL },
			passthroughSampler,
		)
	})

	afterEach(() => {
		vi.restoreAllMocks()
	})

	it('routes exports through fetch with keepalive:true when unloading', async () => {
		exporter.setUnloading(true)
		const result = await new Promise<{ code: ExportResultCode }>(
			(resolve) => exporter.export([fakeSpan()], resolve),
		)

		expect(result.code).toBe(ExportResultCode.SUCCESS)
		expect(fetchSpy).toHaveBeenCalledTimes(1)
		const [url, init] = fetchSpy.mock.calls[0]
		expect(url).toBe(TRACES_URL)
		expect(init).toMatchObject({
			method: 'POST',
			keepalive: true,
			headers: { 'Content-Type': 'application/json' },
		})
		expect(init.body).toBeInstanceOf(Blob)
	})

	it('does not hit fetch when not in unloading mode', async () => {
		// The XHR path is exercised by the OTEL SDK internals. We can't easily
		// assert on XHR here, but we can assert that we didn't take the
		// keepalive-fetch shortcut.
		exporter.setUnloading(false)
		exporter.export([fakeSpan()], () => {})
		// Give the XHR path a tick to schedule.
		await new Promise((r) => setTimeout(r, 0))
		expect(fetchSpy).not.toHaveBeenCalled()
	})

	it('reports failure when the keepalive fetch rejects and no sendBeacon fallback exists', async () => {
		fetchSpy.mockRejectedValueOnce(new Error('network down'))
		const originalSendBeacon = navigator.sendBeacon
		;(navigator as any).sendBeacon = undefined

		exporter.setUnloading(true)
		const result = await new Promise<{
			code: ExportResultCode
			error?: Error
		}>((resolve) => exporter.export([fakeSpan()], resolve))

		expect(result.code).toBe(ExportResultCode.FAILED)
		;(navigator as any).sendBeacon = originalSendBeacon
	})

	it('returns early with no fetch call when sampling drops all items', () => {
		const e = new OTLPTraceExporterBrowserWithXhrRetry(
			{ url: TRACES_URL },
			rejectAllSampler,
		)
		e.setUnloading(true)
		e.export([fakeSpan()], () => {})
		expect(fetchSpy).not.toHaveBeenCalled()
	})
})

describe('OTLPMetricExporterBrowser', () => {
	let fetchSpy: MockInstance
	let exporter: OTLPMetricExporterBrowser

	beforeEach(() => {
		fetchSpy = vi
			.spyOn(globalThis, 'fetch')
			.mockResolvedValue(new Response('OK', { status: 200 }))
		exporter = new OTLPMetricExporterBrowser({ url: METRICS_URL })
	})

	afterEach(() => {
		vi.restoreAllMocks()
	})

	it('routes exports through fetch with keepalive:true when unloading', async () => {
		exporter.setUnloading(true)
		const result = await new Promise<{ code: ExportResultCode }>(
			(resolve) => exporter.export(fakeMetricsPayload(), resolve),
		)

		expect(result.code).toBe(ExportResultCode.SUCCESS)
		expect(fetchSpy).toHaveBeenCalledTimes(1)
		const [url, init] = fetchSpy.mock.calls[0]
		expect(url).toBe(METRICS_URL)
		expect(init).toMatchObject({
			method: 'POST',
			keepalive: true,
		})
	})
})

// Minimal ReadableSpan-shaped object. The default JSON serializer touches a
// handful of fields on each span; fill in what's needed so serialization
// succeeds and produces non-empty output.
function fakeSpan() {
	const now = Date.now() * 1_000_000
	const sec = Math.floor(now / 1e9)
	const nano = now % 1e9
	const resource = { attributes: { 'service.name': 'test' } }
	// Valid W3C trace context IDs, split so code scanners don't flag them as
	// secrets. See https://www.w3.org/TR/trace-context/#traceparent-header.
	const traceId = '0af7651916cd43dd' + '8448eb211c80319c'
	const spanId = 'b7ad6b71' + '69203331'
	return {
		name: 'test-span',
		kind: 0,
		spanContext: () => ({
			traceId,
			spanId,
			traceFlags: 1,
		}),
		parentSpanId: undefined,
		startTime: [sec, nano],
		endTime: [sec, nano],
		status: { code: 0 },
		attributes: {},
		links: [],
		events: [],
		duration: [0, 0],
		ended: true,
		resource,
		instrumentationLibrary: { name: 'test', version: '0.0.0' },
		instrumentationScope: { name: 'test', version: '0.0.0' },
		droppedAttributesCount: 0,
		droppedEventsCount: 0,
		droppedLinksCount: 0,
	} as any
}

function fakeMetricsPayload() {
	return {
		resource: { attributes: {}, merge: () => null },
		scopeMetrics: [],
	} as any
}
