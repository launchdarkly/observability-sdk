import type { ReadableLogRecord } from '@opentelemetry/sdk-logs'
import type { ReadableSpan } from '@opentelemetry/sdk-trace-base'
import { ObservabilityClient } from './ObservabilityClient'
import { InstrumentationManager } from './InstrumentationManager'
import { CustomLogExporter } from '../otel/CustomLogExporter'
import { CustomTraceExporter } from '../otel/CustomTraceExporter'
import { Observability } from '../plugin/observability'
import { _LDObserve } from '../sdk/LDObserve'
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

const USER_KEY = 'context.contextKeys.user'
const DEVICE_KEY = 'context.contextKeys.device'

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

const findSpan = (predicate: (span: ReadableSpan) => boolean) => {
	const span = exportedSpans.find(predicate)
	expect(span).toBeDefined()
	return span!.attributes as Record<string, unknown>
}

const findLog = (body: string) => {
	const log = exportedLogs.find((l) => l.body === body)
	expect(log).toBeDefined()
	return log!.attributes as Record<string, unknown>
}

describe('InstrumentationManager context keys', () => {
	describe('store', () => {
		it('prefixes each context kind', () => {
			const manager = new InstrumentationManager(localOptions)
			expect(manager.getContextKeyAttributes()).toEqual({})

			manager.setContextKeyAttributes({ user: 'u1', device: 'd1' })
			expect(manager.getContextKeyAttributes()).toEqual({
				[USER_KEY]: 'u1',
				[DEVICE_KEY]: 'd1',
			})
			expect(Object.isFrozen(manager.getContextKeyAttributes())).toBe(
				true,
			)
		})

		it('replaces the keys on each call', () => {
			const manager = new InstrumentationManager(localOptions)
			manager.setContextKeyAttributes({ user: 'u1', device: 'd1' })
			manager.setContextKeyAttributes({ device: 'd2' })
			expect(manager.getContextKeyAttributes()).toEqual({
				[DEVICE_KEY]: 'd2',
			})
			manager.setContextKeyAttributes({})
			expect(manager.getContextKeyAttributes()).toEqual({})
		})

		it('adds context keys to metrics alongside global attributes', () => {
			const instrument = { record: vi.fn(), add: vi.fn() }
			const meter = {
				createGauge: vi.fn(() => instrument),
				createCounter: vi.fn(() => instrument),
				createHistogram: vi.fn(() => instrument),
				createUpDownCounter: vi.fn(() => instrument),
			}
			const manager = new InstrumentationManager(localOptions)
			;(manager as any).getMeter = () => meter
			manager.setAttributes({ campaign: 'spring' })
			manager.setContextKeyAttributes({ user: 'u1' })

			const metric = { name: 'm', value: 2, attributes: { own: 'v' } }
			const expected = { campaign: 'spring', [USER_KEY]: 'u1', own: 'v' }
			manager.recordMetric(metric)
			manager.recordHistogram(metric)
			manager.recordCount(metric)
			manager.recordIncr(metric)
			manager.recordUpDownCounter(metric)
			expect(instrument.record).toHaveBeenNthCalledWith(1, 2, expected)
			expect(instrument.record).toHaveBeenNthCalledWith(2, 2, expected)
			expect(instrument.add).toHaveBeenNthCalledWith(1, 2, expected)
			expect(instrument.add).toHaveBeenNthCalledWith(2, 1, expected)
			expect(instrument.add).toHaveBeenNthCalledWith(3, 2, expected)
		})
	})

	describe('identify through the plugin', () => {
		let client: ObservabilityClient
		const [hook] = new Observability(localOptions).getHooks!({
			sdk: { name: 'test-sdk', version: '1.0.0' },
			mobileKey: 'mob-key',
		} as any)

		const identify = (context: any, status = 'completed') =>
			hook.afterIdentify!({ context, timeout: 5 } as any, {}, {
				status,
			} as any)

		beforeAll(async () => {
			_LDObserve._resetForTesting()
			// Identify before the SDK is initialized: the keys are buffered and
			// applied, in order, once it loads.
			identify({ kind: 'device', key: 'device-1' })
			const buffered = _LDObserve
				._getBufferStatus()
				.buffer.map((e) => e.method)
			expect(buffered).toEqual(['setContextKeys', 'recordLog'])

			client = new ObservabilityClient('test-project-id', localOptions)
			_LDObserve._init(client)
			await vi.waitFor(
				() => expect(_LDObserve.isInitialized()).toBe(true),
				{ timeout: 2000 },
			)
		})

		afterAll(async () => {
			;(client as any).errorInstrumentation?.destroy?.()
			await client.stop()
			_LDObserve._resetForTesting()
		})

		it('applies keys from an identify made before init', async () => {
			await client.flush()
			expect(findLog('LD.identify')).toMatchObject({
				[DEVICE_KEY]: 'device-1',
				// The bare keys on the identify log are kept for back-compat.
				device: 'device-1',
			})

			_LDObserve.recordLog('pre-login', 'info')
			await client.flush()
			const log = findLog('pre-login')
			expect(log).toMatchObject({ [DEVICE_KEY]: 'device-1' })
			expect(log).not.toHaveProperty(USER_KEY)
		})

		describe('after a multi-context identify', () => {
			beforeAll(() => {
				identify({
					kind: 'multi',
					user: { key: 'user-1' },
					device: { key: 'device-1' },
				})
			})

			const expected = { [USER_KEY]: 'user-1', [DEVICE_KEY]: 'device-1' }

			it('adds keys to recordLog', async () => {
				_LDObserve.recordLog('post-login', 'info', { own: 'v' })
				await client.flush()
				expect(findLog('post-login')).toMatchObject({
					...expected,
					own: 'v',
				})
			})

			it('adds keys to recordError spans', async () => {
				_LDObserve.recordError(new Error('boom-context'))
				await client.flush()
				expect(
					findSpan(
						(s) =>
							s.attributes['exception.message'] ===
							'boom-context',
					),
				).toMatchObject(expected)
			})

			it('adds keys to console.error spans', async () => {
				console.error('boom-console-context')
				await client.flush()
				expect(
					findSpan((s) =>
						String(s.attributes['exception.message']).includes(
							'boom-console-context',
						),
					),
				).toMatchObject(expected)
			})

			it('adds keys to manual and track spans', async () => {
				_LDObserve.startSpan('manual-context').end()
				_LDObserve.track('signup-context')
				await client.flush()
				expect(
					findSpan((s) => s.name === 'manual-context'),
				).toMatchObject(expected)
				expect(
					findSpan(
						(s) =>
							s.name === 'track' &&
							s.attributes.key === 'signup-context',
					),
				).toMatchObject(expected)
			})

			it('adds keys to flag evaluation spans', async () => {
				hook.afterEvaluation!(
					{ flagKey: 'context-flag', defaultValue: false } as any,
					{},
					{ value: true, variationIndex: 0, reason: null },
				)
				await client.flush()
				expect(
					findSpan(
						(s) =>
							s.attributes['feature_flag.key'] === 'context-flag',
					),
				).toMatchObject(expected)
			})

			it('adds keys to network spans', async () => {
				const url = 'http://127.0.0.1:59999/context-keys'
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
				expect(findSpan(isXhrSpan)).toMatchObject(expected)
			})
		})

		it('replaces keys on re-identify', async () => {
			identify({ kind: 'user', key: 'user-2' })
			_LDObserve.recordLog('re-identified', 'info')
			await client.flush()
			const log = findLog('re-identified')
			expect(log).toMatchObject({ [USER_KEY]: 'user-2' })
			expect(log).not.toHaveProperty(DEVICE_KEY)
		})

		it('sets keys even when identify does not complete', async () => {
			identify({ kind: 'user', key: 'user-3' }, 'error')
			_LDObserve.recordLog('identify-error', 'info')
			await client.flush()
			expect(findLog('identify-error')).toMatchObject({
				[USER_KEY]: 'user-3',
			})
		})
	})
})
