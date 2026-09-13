import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ObserveSDK } from './observe'
import * as otel from '../client/otel'

describe('ObserveSDK metric units', () => {
	let observeImpl: ObserveSDK

	beforeEach(() => {
		observeImpl = new ObserveSDK({
			backendUrl: 'https://pub.highlight.io',
			otel: {
				otlpEndpoint: 'https://otel.highlight.io',
			},
			projectId: '1',
			sessionSecureId: 'test-session',
			environment: 'test',
		})
	})

	it('passes first-party UCUM units when creating instruments', () => {
		const createGauge = vi.fn().mockReturnValue({ record: vi.fn() })
		const createHistogram = vi.fn().mockReturnValue({ record: vi.fn() })
		vi.spyOn(otel, 'getMeter').mockReturnValue({
			createGauge,
			createCounter: vi.fn(),
			createHistogram,
			createUpDownCounter: vi.fn(),
		} as any)

		observeImpl.recordGauge({ name: 'LCP', value: 1200 })
		observeImpl.recordGauge({ name: 'CLS', value: 0.05 })
		observeImpl.recordGauge({ name: 'usedJSHeapSize', value: 1e6 })
		observeImpl.recordHistogram({
			name: 'long_task.duration',
			value: 50,
		})

		expect(createGauge).toHaveBeenCalledWith('LCP', { unit: 'ms' })
		expect(createGauge).toHaveBeenCalledWith('CLS', { unit: '1' })
		expect(createGauge).toHaveBeenCalledWith('usedJSHeapSize', {
			unit: 'By',
		})
		expect(createHistogram).toHaveBeenCalledWith('long_task.duration', {
			unit: 'ms',
		})
	})

	it('lets an explicit metric.unit override the first-party map', () => {
		const createGauge = vi.fn().mockReturnValue({ record: vi.fn() })
		vi.spyOn(otel, 'getMeter').mockReturnValue({
			createGauge,
			createCounter: vi.fn(),
			createHistogram: vi.fn(),
			createUpDownCounter: vi.fn(),
		} as any)

		observeImpl.recordGauge({
			name: 'LCP',
			value: 1,
			unit: 's',
		})

		expect(createGauge).toHaveBeenCalledWith('LCP', { unit: 's' })
	})

	it('omits instrument options for unknown metrics without a unit', () => {
		const createGauge = vi.fn().mockReturnValue({ record: vi.fn() })
		vi.spyOn(otel, 'getMeter').mockReturnValue({
			createGauge,
			createCounter: vi.fn(),
			createHistogram: vi.fn(),
			createUpDownCounter: vi.fn(),
		} as any)

		observeImpl.recordGauge({
			name: 'custom.customer.metric',
			value: 1,
		})

		expect(createGauge).toHaveBeenCalledWith(
			'custom.customer.metric',
			undefined,
		)
	})
})
