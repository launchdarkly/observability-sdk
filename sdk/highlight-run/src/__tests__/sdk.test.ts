import { beforeEach, describe, expect, it, vi } from 'vitest'
import { LDObserve } from '../sdk/LDObserve'
import { LDRecord } from '../sdk/LDRecord'
import type { OTelMetric as Metric } from '../client/types/types'
import type { Attributes } from '@opentelemetry/api'
import { ObserveSDK } from '../sdk/observe'
import { RecordSDK } from '../sdk/record'
import { globalStorage } from '../client/utils/storage'
import { Record } from '../plugins/record'

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
			otlpEndpoint: 'https://otel.highlight.io',
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
			const sdk = new Record('1')
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
			const sdk = new Record('1')
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
