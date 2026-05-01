import { beforeEach, describe, expect, it, vi } from 'vitest'
import { LDObserve } from '../sdk/LDObserve'
import { LDRecord } from '../sdk/LDRecord'
import type { OTelMetric as Metric } from '../client/types/types'
import type { Attributes } from '@opentelemetry/api'
import { ObserveSDK } from '../sdk/observe'
import { RecordSDK } from '../sdk/record'
import { globalStorage } from '../client/utils/storage'
import { Record } from '../plugins/record'
import * as otel from '../client/otel'

describe('SDK', () => {
	let observe: typeof LDObserve
	let record: typeof LDRecord
	let observeImpl: ObserveSDK
	let recordImpl: RecordSDK

	beforeEach(() => {
		// Reset the instances before each test
		observe = LDObserve
		record = LDRecord
		observeImpl = new ObserveSDK({
			backendUrl: 'https://pub.highlight.io',
			otel: {
				otlpEndpoint: 'https://otel.highlight.io',
			},
			projectId: '1',
			sessionSecureId: 'test-session',
			environment: 'test',
		})
		recordImpl = new RecordSDK({
			organizationID: '1',
			environment: 'test',
			sessionSecureID: 'test-session',
		})

		// Mock the graphqlSDK and initializeSession function
		recordImpl.graphqlSDK = {
			initializeSession: vi.fn().mockResolvedValue({
				initializeSession: {
					secure_id: 'test-session',
					project_id: '1',
				},
			}),
		} as any
		observe.load(observeImpl)
		record.load(recordImpl)
	})

	describe('Record Methods', () => {
		it('should handle start and stop', async () => {
			const mockStart = vi.spyOn(recordImpl, 'start')
			const mockStop = vi.spyOn(recordImpl, 'stop')

			await record.start()
			record.stop()

			expect(mockStart).toHaveBeenCalled()
			expect(mockStop).toHaveBeenCalled()
		})

		it('should handle snapshot', async () => {
			const mockSnapshot = vi.spyOn(recordImpl, 'snapshot')

			const canvas = document.createElement('canvas')
			await record.snapshot(canvas)

			expect(mockSnapshot).toHaveBeenCalledWith(canvas)
		})

		it('should construct despite error with storage fallback', async () => {
			vi.stubGlobal('localStorage', {
				getItem: vi.fn(() => {
					throw new Error('get error')
				}),
				setItem: vi.fn(() => {
					throw new Error('set error')
				}),
			})
			const sdk = new Record()
			// trigger sdk to initialize
			sdk.getHooks?.({ sdkKey: 'abc123', sdk: { name: '', version: '' } })
			expect(sdk.record).toBeDefined()
			vi.unstubAllGlobals()
		})

		it('should construct despite error', async () => {
			vi.spyOn(globalStorage, 'getItem').mockImplementation(() => {
				throw new Error('get error')
			})
			vi.stubGlobal('localStorage', {
				getItem: vi.fn(() => {
					throw new Error('get error')
				}),
				setItem: vi.fn(() => {
					throw new Error('set error')
				}),
			})
			const sdk = new Record()
			expect(sdk.record).toBeUndefined()
			vi.unstubAllGlobals()
		})
	})

	describe('Observe Methods', () => {
		it('should handle metric recording', async () => {
			const mockRecordGauge = vi.spyOn(observeImpl, 'recordGauge')
			const mockRecordCount = vi.spyOn(observeImpl, 'recordCount')
			const mockRecordIncr = vi.spyOn(observeImpl, 'recordIncr')
			const mockRecordHistogram = vi.spyOn(observeImpl, 'recordHistogram')
			const mockRecordUpDownCounter = vi.spyOn(
				observeImpl,
				'recordUpDownCounter',
			)

			const metric: Metric = {
				name: 'test.metric',
				value: 100,
				attributes: { test: 'value' },
			}

			observe.recordGauge(metric)
			observe.recordCount(metric)
			observe.recordIncr({
				name: 'test.metric',
				attributes: { test: 'value' } as Attributes,
			})
			observe.recordHistogram(metric)
			observe.recordUpDownCounter(metric)

			expect(mockRecordGauge).toHaveBeenCalledWith(metric)
			expect(mockRecordCount).toHaveBeenCalledWith(metric)
			expect(mockRecordIncr).toHaveBeenCalledWith({
				name: 'test.metric',
				attributes: { test: 'value' } as Attributes,
			})
			expect(mockRecordHistogram).toHaveBeenCalledWith(metric)
			expect(mockRecordUpDownCounter).toHaveBeenCalledWith(metric)
		})

		it('should include LD context keys in metric attributes', () => {
			const gauge = { record: vi.fn() }
			const counter = { add: vi.fn() }
			const histogram = { record: vi.fn() }
			const upDownCounter = { add: vi.fn() }
			vi.spyOn(otel, 'getMeter').mockReturnValue({
				createGauge: vi.fn().mockReturnValue(gauge),
				createCounter: vi.fn().mockReturnValue(counter),
				createHistogram: vi.fn().mockReturnValue(histogram),
				createUpDownCounter: vi.fn().mockReturnValue(upDownCounter),
			} as any)

			observeImpl.setLDContextKeyAttributes({ user: 'alice' })

			observeImpl.recordGauge({
				name: 'gauge.test',
				value: 1,
				attributes: { foo: 'bar' },
			})
			observeImpl.recordCount({
				name: 'count.test',
				value: 2,
				attributes: { foo: 'bar' },
			})
			observeImpl.recordHistogram({
				name: 'hist.test',
				value: 3,
				attributes: { foo: 'bar' },
			})
			observeImpl.recordUpDownCounter({
				name: 'updown.test',
				value: 4,
				attributes: { foo: 'bar' },
			})

			const expected = expect.objectContaining({
				'context.contextKeys.user': 'alice',
				foo: 'bar',
			})
			expect(gauge.record).toHaveBeenCalledWith(1, expected)
			expect(counter.add).toHaveBeenCalledWith(2, expected)
			expect(histogram.record).toHaveBeenCalledWith(3, expected)
			expect(upDownCounter.add).toHaveBeenCalledWith(4, expected)
		})

		it('caller attributes win over LD context keys with the same name', () => {
			const gauge = { record: vi.fn() }
			vi.spyOn(otel, 'getMeter').mockReturnValue({
				createGauge: vi.fn().mockReturnValue(gauge),
				createCounter: vi.fn(),
				createHistogram: vi.fn(),
				createUpDownCounter: vi.fn(),
			} as any)

			observeImpl.setLDContextKeyAttributes({ user: 'alice' })
			observeImpl.recordGauge({
				name: 'gauge.override',
				value: 1,
				attributes: { 'context.contextKeys.user': 'bob' },
			})

			expect(gauge.record).toHaveBeenCalledWith(
				1,
				expect.objectContaining({
					'context.contextKeys.user': 'bob',
				}),
			)
		})

		it('should handle error recording', async () => {
			const mockRecordError = vi.spyOn(observeImpl, 'recordError')

			const error = new Error('Test error')
			const payload = { errorCode: 'E123' }

			observe.recordError(error, 'Error message', payload)

			expect(mockRecordError).toHaveBeenCalledWith(
				error,
				'Error message',
				payload,
				undefined,
				undefined,
			)
		})
	})
})
