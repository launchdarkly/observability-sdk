import type { ReadableLogRecord } from '@opentelemetry/sdk-logs'
import type { ReadableSpan } from '@opentelemetry/sdk-trace-base'
import type { LogRecordProcessor } from '@opentelemetry/sdk-logs'
import type { SpanProcessor } from '@opentelemetry/sdk-trace-web'
import {
	afterAll,
	beforeAll,
	beforeEach,
	describe,
	expect,
	it,
	vi,
} from 'vitest'
import { ObservabilityClient } from './ObservabilityClient'
import { InstrumentationManager } from './InstrumentationManager'
import { CustomLogExporter } from '../otel/CustomLogExporter'
import { CustomTraceExporter } from '../otel/CustomTraceExporter'
import { globalAttributes } from '../otel/GlobalAttributes'
import { _LDObserve } from '../sdk/LDObserve'

// FetchInstrumentation decides at module load whether it runs under Node (and
// then refuses to patch fetch), so pretend to be a non-Node runtime as React
// Native is.
const originalRelease = vi.hoisted(() => {
	const release = process.release
	Object.defineProperty(process, 'release', {
		value: { ...release, name: 'react-native' },
		configurable: true,
	})
	return release
})

const TEST_FETCH_URL = 'http://example.test/api'

const localOptions = {
	projectId: 'test-project-id',
	serviceName: 'test-service',
	backendUrl: 'http://127.0.0.1:1',
	otlpEndpoint: 'http://127.0.0.1:1',
	serviceVersion: '1.0.0',
	resourceAttributes: {},
	customHeaders: {},
	sessionTimeout: 15 * 60 * 1000,
	debug: false,
	disableErrorTracking: true,
	disableLogs: false,
	disableTraces: false,
	disableMetrics: true,
	tracingOrigins: false as const,
	urlBlocklist: [],
	networkRecording: {},
	contextFriendlyName: () => undefined,
	flagExposureDedupeWindowMillis: 0,
	flagExposureDedupeMaxSize: 2000,
	maxBufferSize: 1000,
	uploadIntervalMillis: 10 * 60 * 1000,
	spanProcessors: [],
	logProcessors: [],
}

const exportedLogs: ReadableLogRecord[] = []
const exportedSpans: ReadableSpan[] = []

const findSpan = (name: string) =>
	exportedSpans.find((span) => span.name === name)
const findLog = (body: string) =>
	exportedLogs.find((record) => record.body === body)

describe('global attributes and custom processors', () => {
	const seenAtSpanStart: Record<string, unknown> = {}
	const seenAtLogEmit: Record<string, unknown> = {}

	const mutatingSpanProcessor: SpanProcessor = {
		onStart: (span) => {
			seenAtSpanStart[span.name] = span.attributes['app.tier']
			span.setAttribute('user.processor', 'span')
		},
		onEnd: () => {},
		forceFlush: () => Promise.resolve(),
		shutdown: () => Promise.resolve(),
	}
	const throwingSpanProcessor: SpanProcessor = {
		onStart: () => {
			throw new Error('span onStart')
		},
		onEnd: () => {
			throw new Error('span onEnd')
		},
		forceFlush: () => Promise.reject(new Error('span forceFlush')),
		shutdown: () => Promise.resolve(),
	}
	const mutatingLogProcessor: LogRecordProcessor = {
		onEmit: (record) => {
			seenAtLogEmit[String(record.body)] = record.attributes['app.tier']
			record.setAttribute('user.processor', 'log')
		},
		forceFlush: () => Promise.resolve(),
		shutdown: () => Promise.resolve(),
	}
	const throwingLogProcessor: LogRecordProcessor = {
		onEmit: () => {
			throw new Error('log onEmit')
		},
		forceFlush: () => Promise.resolve(),
		shutdown: () => Promise.resolve(),
	}

	let client: ObservabilityClient
	let originalFetch: typeof globalThis.fetch

	beforeAll(async () => {
		vi.spyOn(CustomLogExporter.prototype, 'export').mockImplementation(
			(items, resultCallback) => {
				exportedLogs.push(...items)
				resultCallback({ code: 0 })
			},
		)
		vi.spyOn(CustomTraceExporter.prototype, 'export').mockImplementation(
			(items, resultCallback) => {
				exportedSpans.push(...items)
				resultCallback({ code: 0 })
			},
		)
		originalFetch = globalThis.fetch
		globalThis.fetch = vi.fn(async (input: RequestInfo | URL) => {
			if (String(input).startsWith(TEST_FETCH_URL)) {
				return new Response('ok', { status: 200 })
			}
			throw new Error('network disabled in tests')
		}) as typeof globalThis.fetch

		_LDObserve._resetForTesting()
		globalAttributes._resetForTesting()

		// Called before the client exists: must be buffered and still apply.
		_LDObserve.setAttributes({
			'app.tier': 'gold',
			shared: 'global',
			'highlight.session_id': 'spoofed',
			'session.id': 'spoofed',
		})
		_LDObserve.recordLog('before-init', 'info')

		client = new ObservabilityClient('test-project-id', {
			...localOptions,
			disableErrorTracking: false,
			spanProcessors: [throwingSpanProcessor, mutatingSpanProcessor],
			logProcessors: [throwingLogProcessor, mutatingLogProcessor],
		})
		_LDObserve._init(client)
		await vi.waitFor(() => expect(_LDObserve.isInitialized()).toBe(true), {
			timeout: 2000,
		})
		await client.flush()
	})

	beforeEach(() => {
		// The before-init log is asserted in the first test; keep it around.
		exportedSpans.length = 0
	})

	afterAll(async () => {
		await client.stop()
		globalAttributes._resetForTesting()
		_LDObserve._resetForTesting()
		globalThis.fetch = originalFetch
		Object.defineProperty(process, 'release', {
			value: originalRelease,
			configurable: true,
		})
		vi.restoreAllMocks()
	})

	it('applies attributes set before init to telemetry recorded before init', () => {
		const record = findLog('before-init')
		expect(record).toBeDefined()
		expect(record!.attributes['app.tier']).toBe('gold')
		expect(record!.attributes['user.processor']).toBe('log')
	})

	it('never sets reserved session keys from globals', async () => {
		const sessionId = _LDObserve.getSessionInfo().sessionId
		_LDObserve.startSpan('reserved-span').end()
		_LDObserve.recordLog('reserved-log', 'info')
		await client.flush()

		const span = findSpan('reserved-span')!
		expect(span.attributes['highlight.session_id']).toBeUndefined()
		expect(span.attributes['session.id']).toBeUndefined()
		expect(span.resource.attributes['session.id']).toBe(sessionId)

		const record = findLog('reserved-log')!
		expect(record.attributes['highlight.session_id']).toBe(sessionId)
		expect(record.attributes['session.id']).toBeUndefined()
	})

	it('applies globals to manual spans, with per-call attributes winning', async () => {
		_LDObserve
			.startSpan('manual-span', { attributes: { shared: 'call' } })
			.end()
		await client.flush()

		const span = findSpan('manual-span')!
		expect(span.attributes['app.tier']).toBe('gold')
		expect(span.attributes.shared).toBe('call')
	})

	it('applies globals to auto-instrumented fetch spans', async () => {
		const response = await fetch(TEST_FETCH_URL)
		expect(response.status).toBe(200)

		await vi.waitFor(
			async () => {
				await client.flush()
				const fetchSpan = exportedSpans.find(
					(span) =>
						span.attributes['http.url'] === TEST_FETCH_URL ||
						span.attributes['url.full'] === TEST_FETCH_URL,
				)
				expect(fetchSpan).toBeDefined()
				expect(fetchSpan!.attributes['app.tier']).toBe('gold')
				expect(fetchSpan!.attributes['user.processor']).toBe('span')
			},
			{ timeout: 3000, interval: 100 },
		)
	})

	it('applies globals to error spans, with per-call attributes winning', async () => {
		_LDObserve.recordError(new Error('boom'), { shared: 'call' })
		await client.flush()

		const span = exportedSpans.find(
			(s) => s.attributes['exception.message'] === 'boom',
		)!
		expect(span).toBeDefined()
		expect(span.attributes['app.tier']).toBe('gold')
		expect(span.attributes.shared).toBe('call')
	})

	it('applies globals to logs, with per-call attributes winning', async () => {
		_LDObserve.recordLog('log-with-globals', 'info', { shared: 'call' })
		await client.flush()

		const record = findLog('log-with-globals')!
		expect(record.attributes['app.tier']).toBe('gold')
		expect(record.attributes.shared).toBe('call')
	})

	it('stops applying removed attributes', async () => {
		_LDObserve.setAttributes({ temporary: 'yes' })
		_LDObserve.startSpan('with-temporary').end()
		_LDObserve.removeAttributes(['temporary'])
		_LDObserve.startSpan('without-temporary').end()
		await client.flush()

		expect(findSpan('with-temporary')!.attributes.temporary).toBe('yes')
		expect(
			findSpan('without-temporary')!.attributes.temporary,
		).toBeUndefined()
	})

	it('runs user processors after globals and exports their mutations despite a throwing processor', async () => {
		_LDObserve.startSpan('processed-span').end()
		_LDObserve.recordLog('processed-log', 'info')
		await client.flush()

		expect(seenAtSpanStart['processed-span']).toBe('gold')
		expect(findSpan('processed-span')!.attributes['user.processor']).toBe(
			'span',
		)
		expect(seenAtLogEmit['processed-log']).toBe('gold')
		expect(findLog('processed-log')!.attributes['user.processor']).toBe(
			'log',
		)
	})
})

describe('InstrumentationManager metrics with global attributes', () => {
	beforeEach(() => {
		globalAttributes._resetForTesting()
	})

	afterAll(() => {
		globalAttributes._resetForTesting()
	})

	const makeManager = () => {
		const instrument = { record: vi.fn(), add: vi.fn() }
		const meter = {
			createGauge: vi.fn(() => instrument),
			createCounter: vi.fn(() => instrument),
			createHistogram: vi.fn(() => instrument),
			createUpDownCounter: vi.fn(() => instrument),
		}
		const manager = new InstrumentationManager(localOptions)
		;(manager as any).getMeter = () => meter
		return { manager, instrument }
	}

	it('merges globals into every metric type, with per-call attributes winning', () => {
		const { manager, instrument } = makeManager()
		manager.setAttributes({ 'app.tier': 'gold', shared: 'global' })

		const metric = { name: 'm', value: 5, attributes: { shared: 'call' } }
		manager.recordMetric(metric)
		manager.recordHistogram(metric)
		manager.recordCount(metric)
		manager.recordIncr(metric)
		manager.recordUpDownCounter(metric)

		const expected = { 'app.tier': 'gold', shared: 'call' }
		expect(instrument.record).toHaveBeenNthCalledWith(1, 5, expected)
		expect(instrument.record).toHaveBeenNthCalledWith(2, 5, expected)
		expect(instrument.add).toHaveBeenNthCalledWith(1, 5, expected)
		expect(instrument.add).toHaveBeenNthCalledWith(2, 1, expected)
		expect(instrument.add).toHaveBeenNthCalledWith(3, 5, expected)
	})

	it('passes metric attributes through unchanged when there are no globals', () => {
		const { manager, instrument } = makeManager()

		manager.recordMetric({ name: 'm', value: 1 })

		expect(instrument.record).toHaveBeenCalledWith(1, undefined)
	})

	it('logs ignored reserved keys in debug mode', () => {
		const logSpy = vi.spyOn(console, 'log').mockImplementation(() => {})
		const manager = new InstrumentationManager({
			...localOptions,
			debug: true,
		})

		manager.setAttributes({ 'session.id': 'x', ok: 1 })

		expect(logSpy).toHaveBeenCalledWith(
			'[InstrumentationManager]',
			'setAttributes ignored reserved or invalid keys:',
			['session.id'],
		)
		expect(globalAttributes.getAttributes()).toEqual({ ok: 1 })
		logSpy.mockRestore()
	})
})
