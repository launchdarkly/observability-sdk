import type { ReadableLogRecord } from '@opentelemetry/sdk-logs'
import type { ReadableSpan } from '@opentelemetry/sdk-trace-base'
import { ObservabilityClient } from './ObservabilityClient'
import { InstrumentationManager } from './InstrumentationManager'
import { CustomLogExporter } from '../otel/CustomLogExporter'
import { CustomTraceExporter } from '../otel/CustomTraceExporter'
import { Observability } from '../plugin/observability'
import {
	afterAll,
	beforeAll,
	beforeEach,
	describe,
	expect,
	it,
	vi,
} from 'vitest'

// Local endpoints that fail fast (connection refused) so nothing reaches the
// network.
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
	disableErrorTracking: false,
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
}

const exportedLogs: ReadableLogRecord[] = []
const exportedSpans: ReadableSpan[] = []

beforeAll(() => {
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
})

beforeEach(() => {
	exportedLogs.length = 0
	exportedSpans.length = 0
})

describe('InstrumentationManager global attributes', () => {
	describe('attribute store', () => {
		it('sets, overwrites and removes attributes', () => {
			const manager = new InstrumentationManager(localOptions)
			manager.setAttributes({ a: 'x', b: 1 })
			manager.setAttributes({ a: 'y', c: [true, false] })
			expect(manager.getGlobalAttributes()).toEqual({
				a: 'y',
				b: 1,
				c: [true, false],
			})

			manager.removeAttributes(['b', 'missing'])
			expect(manager.getGlobalAttributes()).toEqual({
				a: 'y',
				c: [true, false],
			})
		})

		it('removes a key set to null or undefined', () => {
			const manager = new InstrumentationManager(localOptions)
			manager.setAttributes({ a: 'x', b: 'y' })
			manager.setAttributes({ a: null, b: undefined })
			expect(manager.getGlobalAttributes()).toEqual({})
		})

		it('ignores reserved keys', () => {
			const manager = new InstrumentationManager(localOptions)
			manager.setAttributes({
				'highlight.session_id': 'x',
				'context.contextKeys.user': 'x',
				'launchdarkly.anything': 'x',
				'session.id': 'x',
				allowed: 'ok',
			})
			expect(manager.getGlobalAttributes()).toEqual({ allowed: 'ok' })
		})

		it('returns a frozen snapshot that only changes on update', () => {
			const manager = new InstrumentationManager(localOptions)
			manager.setAttributes({ a: 'x' })
			const snapshot = manager.getGlobalAttributes()
			expect(Object.isFrozen(snapshot)).toBe(true)
			expect(manager.getGlobalAttributes()).toBe(snapshot)
			manager.setAttributes({ a: 'y' })
			expect(manager.getGlobalAttributes()).not.toBe(snapshot)
			expect(snapshot).toEqual({ a: 'x' })
		})
	})

	describe('metrics', () => {
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
			manager.setAttributes({ campaign: 'spring', shared: 'global' })
			return { manager, instrument }
		}

		const expected = {
			campaign: 'spring',
			shared: 'per-call',
			own: 'value',
		}
		const metric = {
			name: 'm',
			value: 2,
			attributes: { shared: 'per-call', own: 'value' },
		}

		it('adds global attributes to gauges and histograms', () => {
			const { manager, instrument } = makeManager()
			manager.recordMetric(metric)
			manager.recordHistogram(metric)
			expect(instrument.record).toHaveBeenNthCalledWith(1, 2, expected)
			expect(instrument.record).toHaveBeenNthCalledWith(2, 2, expected)
		})

		it('adds global attributes to counters and up/down counters', () => {
			const { manager, instrument } = makeManager()
			manager.recordCount(metric)
			manager.recordIncr(metric)
			manager.recordUpDownCounter(metric)
			expect(instrument.add).toHaveBeenNthCalledWith(1, 2, expected)
			expect(instrument.add).toHaveBeenNthCalledWith(2, 1, expected)
			expect(instrument.add).toHaveBeenNthCalledWith(3, 2, expected)
		})

		it('stops adding removed attributes', () => {
			const { manager, instrument } = makeManager()
			manager.removeAttributes(['campaign', 'shared'])
			manager.recordMetric({ name: 'm', value: 1 })
			expect(instrument.record).toHaveBeenCalledWith(1, {})
		})
	})

	describe('integration through the client', () => {
		let client: ObservabilityClient

		const findSpan = (predicate: (span: ReadableSpan) => boolean) => {
			const span = exportedSpans.find(predicate)
			expect(span).toBeDefined()
			return span!.attributes as Record<string, unknown>
		}

		beforeAll(async () => {
			client = new ObservabilityClient('test-project-id', localOptions)
			await client.whenInitialized()
			await client.flush()
		})

		beforeEach(() => {
			client.setAttributes({ campaign: 'spring', shared: 'global' })
		})

		afterAll(async () => {
			;(client as any).errorInstrumentation?.destroy?.()
			await client.stop()
		})

		it('adds attributes to recordLog with per-call precedence', async () => {
			client.recordLog('hello', 'info', { shared: 'per-call' })
			await client.flush()

			const log = exportedLogs.find((l) => l.body === 'hello')
			expect(log?.attributes).toMatchObject({
				campaign: 'spring',
				shared: 'per-call',
				'log.source': 'react-native-plugin',
			})
		})

		it('adds attributes to recordError spans', async () => {
			client.consumeCustomError(new Error('boom-global'), {
				shared: 'per-call',
			})
			await client.flush()

			expect(
				findSpan(
					(s) => s.attributes['exception.message'] === 'boom-global',
				),
			).toMatchObject({ campaign: 'spring', shared: 'per-call' })
		})

		it('adds attributes to console.error spans', async () => {
			console.error('boom-console-global')
			await client.flush()

			expect(
				findSpan((s) =>
					String(s.attributes['exception.message']).includes(
						'boom-console-global',
					),
				),
			).toMatchObject({ campaign: 'spring', shared: 'global' })
		})

		it('adds attributes to track spans', async () => {
			client.track('signup', { shared: 'per-call' })
			await client.flush()

			expect(
				findSpan(
					(s) => s.name === 'track' && s.attributes.key === 'signup',
				),
			).toMatchObject({ campaign: 'spring', shared: 'per-call' })
		})

		it('adds attributes to manual spans', async () => {
			client
				.startSpan('manual', { attributes: { shared: 'per-call' } })
				.end()
			await client.flush()

			expect(findSpan((s) => s.name === 'manual')).toMatchObject({
				campaign: 'spring',
				shared: 'per-call',
			})
		})

		it('adds attributes to flag evaluation spans', async () => {
			const [hook] = new Observability(localOptions).getHooks!({
				sdk: { name: 'test-sdk', version: '1.0.0' },
				mobileKey: 'mob-key',
			} as any)
			hook.afterEvaluation!(
				{ flagKey: 'my-flag', defaultValue: false } as any,
				{},
				{ value: true, variationIndex: 0, reason: null },
			)
			await client.flush()

			expect(
				findSpan((s) => s.attributes['feature_flag.key'] === 'my-flag'),
			).toMatchObject({ campaign: 'spring', shared: 'global' })
		})

		it('adds attributes to XHR spans', async () => {
			const url = 'http://127.0.0.1:59999/global-attributes'
			await new Promise<void>((resolve) => {
				const xhr = new XMLHttpRequest()
				xhr.onloadend = () => resolve()
				xhr.open('GET', url)
				xhr.send()
			})
			const isXhrSpan = (s: ReadableSpan) =>
				Object.values(s.attributes).some((v) => v === url)
			await vi.waitFor(async () => {
				await client.flush()
				expect(exportedSpans.some(isXhrSpan)).toBe(true)
			})

			expect(findSpan(isXhrSpan)).toMatchObject({
				campaign: 'spring',
				shared: 'global',
			})
		})

		it('stops adding removed attributes', async () => {
			client.removeAttributes(['campaign'])
			client.setAttributes({ shared: null })
			client.recordLog('after-remove', 'info')
			client.startSpan('after-remove').end()
			await client.flush()

			const log = exportedLogs.find((l) => l.body === 'after-remove')
			expect(log?.attributes).not.toHaveProperty('campaign')
			expect(log?.attributes).not.toHaveProperty('shared')
			const span = findSpan((s) => s.name === 'after-remove')
			expect(span).not.toHaveProperty('campaign')
			expect(span).not.toHaveProperty('shared')
		})

		it('does not override SDK attributes', async () => {
			client.recordLog('reserved', 'info')
			client.setAttributes({ 'log.source': 'user' })
			client.recordLog('reserved-after', 'info')
			await client.flush()

			const log = exportedLogs.find((l) => l.body === 'reserved-after')
			expect(log?.attributes?.['log.source']).toBe('react-native-plugin')
			client.removeAttributes(['log.source'])
		})
	})
})
