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
import { OTLPMetricExporter } from '@opentelemetry/exporter-metrics-otlp-http'
import {
	OTLPMetricExporterBrowser,
	OTLPTraceExporterBrowserWithXhrRetry,
} from './exporter'
import type { ExportSampler } from './sampling/ExportSampler'
import { ConsoleListener } from '../listeners/console-listener'
import type { ConsoleMessage } from '../types/shared-types'
import { setSdkDebugLogging } from '../utils/sdkLogger'

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

	it('falls back to sendBeacon when the keepalive fetch rejects (e.g. keepalive body size overflow)', async () => {
		// Per WHATWG Fetch §4.6, keepalive body size overflow is an async
		// promise rejection — not a synchronous throw — so sendBeacon must
		// be tried from the rejection handler, not from the sync catch.
		fetchSpy.mockRejectedValueOnce(new TypeError('Failed to fetch'))
		const beaconSpy = vi.fn().mockReturnValue(true)
		const originalSendBeacon = navigator.sendBeacon
		;(navigator as any).sendBeacon = beaconSpy

		exporter.setUnloading(true)
		const result = await new Promise<{ code: ExportResultCode }>(
			(resolve) => exporter.export([fakeSpan()], resolve),
		)

		expect(result.code).toBe(ExportResultCode.SUCCESS)
		expect(beaconSpy).toHaveBeenCalledTimes(1)
		expect(beaconSpy.mock.calls[0][0]).toBe(TRACES_URL)
		expect(beaconSpy.mock.calls[0][1]).toBeInstanceOf(Blob)
		;(navigator as any).sendBeacon = originalSendBeacon
	})

	it('reports failure when sendBeacon returns false after the fetch rejects', async () => {
		fetchSpy.mockRejectedValueOnce(new TypeError('Failed to fetch'))
		const beaconSpy = vi.fn().mockReturnValue(false)
		const originalSendBeacon = navigator.sendBeacon
		;(navigator as any).sendBeacon = beaconSpy

		exporter.setUnloading(true)
		const result = await new Promise<{ code: ExportResultCode }>(
			(resolve) => exporter.export([fakeSpan()], resolve),
		)

		expect(result.code).toBe(ExportResultCode.FAILED)
		expect(beaconSpy).toHaveBeenCalledTimes(1)
		;(navigator as any).sendBeacon = originalSendBeacon
	})

	it('does not fall back to sendBeacon when the server responds with an error status', async () => {
		fetchSpy.mockResolvedValueOnce(new Response('bad', { status: 500 }))
		const beaconSpy = vi.fn().mockReturnValue(true)
		const originalSendBeacon = navigator.sendBeacon
		;(navigator as any).sendBeacon = beaconSpy

		exporter.setUnloading(true)
		const result = await new Promise<{ code: ExportResultCode }>(
			(resolve) => exporter.export([fakeSpan()], resolve),
		)

		expect(result.code).toBe(ExportResultCode.FAILED)
		expect(beaconSpy).not.toHaveBeenCalled()
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

describe('OTLPMetricExporterBrowser export failures', () => {
	let exporter: OTLPMetricExporterBrowser
	let errorSpy: MockInstance
	let warnSpy: MockInstance
	let restoreListener: (() => void) | undefined

	// Make every underlying export fail the way the browser transport does
	// when the request errors or the server answers with a retryable status.
	const failAllExports = () =>
		vi
			.spyOn(OTLPMetricExporter.prototype, 'export')
			.mockImplementation((_items, resultCallback) => {
				const error = new Error('Export failed with retryable status')
				error.name = 'OTLPExporterError'
				resultCallback({ code: ExportResultCode.FAILED, error })
			})

	const exportUntilExhausted = async () => {
		const done = new Promise<{ code: ExportResultCode; error?: Error }>(
			(resolve) => exporter.export(fakeMetricsPayload(), resolve),
		)
		// Walk through every retry back-off.
		await vi.advanceTimersByTimeAsync(60_000)
		return done
	}

	beforeEach(() => {
		vi.useFakeTimers()
		errorSpy = vi.spyOn(console, 'error').mockImplementation(() => {})
		warnSpy = vi.spyOn(console, 'warn').mockImplementation(() => {})
		exporter = new OTLPMetricExporterBrowser({ url: METRICS_URL })
	})

	afterEach(() => {
		restoreListener?.()
		restoreListener = undefined
		setSdkDebugLogging(false)
		vi.useRealTimers()
		vi.restoreAllMocks()
	})

	it('gives up after the retries and stays silent on the console by default', async () => {
		const exportSpy = failAllExports()
		setSdkDebugLogging(false)

		const result = await exportUntilExhausted()

		expect(result.code).toBe(ExportResultCode.FAILED)
		expect(result.error?.message).toBe(
			'Export failed with retryable status',
		)
		expect(exportSpy.mock.calls.length).toBeGreaterThan(1)
		expect(errorSpy).not.toHaveBeenCalled()
		expect(warnSpy).not.toHaveBeenCalled()
	})

	it('reports the failure on the console in debug mode', async () => {
		failAllExports()
		setSdkDebugLogging(true)

		const result = await exportUntilExhausted()

		expect(result.code).toBe(ExportResultCode.FAILED)
		expect(errorSpy).toHaveBeenCalledTimes(1)
		expect(errorSpy.mock.calls[0][0]).toBe(
			'[@launchdarkly/observability] failed to export OTeL metrics: Export failed with retryable status',
		)
		expect(warnSpy.mock.calls.length).toBeGreaterThan(0)
		expect(warnSpy.mock.calls[0][0]).toContain(
			'failed to export OTeL metrics: Export failed with retryable status',
		)
	})

	it('is never recorded as a customer log record, even in debug mode', async () => {
		failAllExports()
		setSdkDebugLogging(true)
		const recorded: ConsoleMessage[] = []
		// Installed after the spies, so it wraps them like it would wrap the
		// application's console.
		restoreListener = ConsoleListener((c) => recorded.push(c), {
			level: ['error', 'warn'],
			logger: console,
			stringifyOptions: {
				depthOfLimit: 10,
				numOfKeysLimit: 100,
				stringLengthLimit: 1000,
			},
		})

		await exportUntilExhausted()

		expect(errorSpy).toHaveBeenCalledTimes(1)
		expect(recorded).toHaveLength(0)
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
