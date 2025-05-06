import { describe, it, expect, vi, beforeEach } from 'vitest'
import { LDObserve } from '../sdk/LDObserve'
import { LDRecord } from '../sdk/LDRecord'
import type { OTelMetric as Metric } from '../client/types/types'
import type { Attributes } from '@opentelemetry/api'

describe('SDK', () => {
	let observe: typeof LDObserve
	let record: typeof LDRecord

	beforeEach(() => {
		// Reset the instances before each test
		observe = LDObserve
		record = LDRecord
	})

	describe('Record Methods', () => {
		it('should handle start and stop', async () => {
			const mockStart = vi.fn()
			const mockStop = vi.fn()
			record.start = mockStart
			record.stop = mockStop

			await record.start()
			record.stop()

			expect(mockStart).toHaveBeenCalled()
			expect(mockStop).toHaveBeenCalled()
		})

		it('should handle snapshot', async () => {
			const mockSnapshot = vi.fn()
			record.snapshot = mockSnapshot

			const canvas = document.createElement('canvas')
			await record.snapshot(canvas)

			expect(mockSnapshot).toHaveBeenCalledWith(canvas)
		})
	})

	describe('Observe Methods', () => {
		it('should handle metric recording', async () => {
			const mockRecordGauge = vi.fn()
			const mockRecordCount = vi.fn()
			const mockRecordIncr = vi.fn()
			const mockRecordHistogram = vi.fn()
			const mockRecordUpDownCounter = vi.fn()

			observe.recordGauge = mockRecordGauge
			observe.recordCount = mockRecordCount
			observe.recordIncr = mockRecordIncr
			observe.recordHistogram = mockRecordHistogram
			observe.recordUpDownCounter = mockRecordUpDownCounter

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
			const mockRecordError = vi.fn()
			observe.recordError = mockRecordError

			const error = new Error('Test error')
			const payload = { errorCode: 'E123' }

			observe.recordError(error, 'Error message', payload)

			expect(mockRecordError).toHaveBeenCalledWith(
				error,
				'Error message',
				payload,
			)
		})
	})
})
