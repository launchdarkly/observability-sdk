import { describe, expect, it } from 'vitest'
import {
	FIRST_PARTY_METRIC_UNITS,
	metricInstrumentOptions,
} from './metricUnits'

describe('metricInstrumentOptions', () => {
	it('returns first-party map units for known metric names', () => {
		expect(metricInstrumentOptions('LCP')).toEqual({ unit: 'ms' })
		expect(metricInstrumentOptions('CLS')).toEqual({ unit: '1' })
		expect(metricInstrumentOptions('usedJSHeapSize')).toEqual({
			unit: 'By',
		})
		expect(metricInstrumentOptions('DeviceMemory')).toEqual({
			unit: 'MiBy',
		})
		expect(metricInstrumentOptions('long_task.duration')).toEqual({
			unit: 'ms',
		})
	})

	it('lets an explicit unit override the first-party map', () => {
		expect(metricInstrumentOptions('LCP', 's')).toEqual({ unit: 's' })
	})

	it('returns undefined for unknown metrics without an explicit unit', () => {
		expect(
			metricInstrumentOptions('custom.customer.metric'),
		).toBeUndefined()
	})

	it('covers every first-party name with a non-empty unit', () => {
		for (const [name, unit] of Object.entries(FIRST_PARTY_METRIC_UNITS)) {
			expect(unit.length).toBeGreaterThan(0)
			expect(metricInstrumentOptions(name)).toEqual({ unit })
		}
	})
})
