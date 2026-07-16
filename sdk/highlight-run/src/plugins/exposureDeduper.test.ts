import { describe, expect, it } from 'vitest'
import {
	DEFAULT_FLAG_EXPOSURE_DEDUPE_WINDOW_MILLIS,
	ExposureDeduper,
} from './exposureDeduper'

describe('ExposureDeduper', () => {
	it('records the first exposure for a key', () => {
		const deduper = new ExposureDeduper(1000)
		expect(deduper.shouldRecord('a', 0)).toBe(true)
	})

	it('deduplicates repeated exposures within the window', () => {
		const deduper = new ExposureDeduper(1000)
		expect(deduper.shouldRecord('a', 0)).toBe(true)
		expect(deduper.shouldRecord('a', 500)).toBe(false)
		expect(deduper.shouldRecord('a', 999)).toBe(false)
	})

	it('records again once the window has elapsed', () => {
		const deduper = new ExposureDeduper(1000)
		expect(deduper.shouldRecord('a', 0)).toBe(true)
		// Exactly at the boundary is still considered outside the window.
		expect(deduper.shouldRecord('a', 1000)).toBe(true)
		expect(deduper.shouldRecord('a', 1500)).toBe(false)
	})

	it('tracks distinct keys independently', () => {
		const deduper = new ExposureDeduper(1000)
		expect(deduper.shouldRecord('a', 0)).toBe(true)
		expect(deduper.shouldRecord('b', 0)).toBe(true)
		expect(deduper.shouldRecord('a', 100)).toBe(false)
		expect(deduper.shouldRecord('b', 100)).toBe(false)
	})

	it('disables deduplication when the window is zero', () => {
		const deduper = new ExposureDeduper(0)
		expect(deduper.shouldRecord('a', 0)).toBe(true)
		expect(deduper.shouldRecord('a', 0)).toBe(true)
		expect(deduper.shouldRecord('a', 1)).toBe(true)
	})

	it('disables deduplication when the window is negative', () => {
		const deduper = new ExposureDeduper(-1)
		expect(deduper.shouldRecord('a', 0)).toBe(true)
		expect(deduper.shouldRecord('a', 0)).toBe(true)
	})

	it('re-records after reset', () => {
		const deduper = new ExposureDeduper(1000)
		expect(deduper.shouldRecord('a', 0)).toBe(true)
		expect(deduper.shouldRecord('a', 100)).toBe(false)
		deduper.reset()
		expect(deduper.shouldRecord('a', 100)).toBe(true)
	})

	it('defaults to a 3 minute window', () => {
		expect(DEFAULT_FLAG_EXPOSURE_DEDUPE_WINDOW_MILLIS).toBe(3 * 60 * 1000)
		const deduper = new ExposureDeduper()
		expect(deduper.shouldRecord('a', 0)).toBe(true)
		expect(
			deduper.shouldRecord(
				'a',
				DEFAULT_FLAG_EXPOSURE_DEDUPE_WINDOW_MILLIS - 1,
			),
		).toBe(false)
		expect(
			deduper.shouldRecord('a', DEFAULT_FLAG_EXPOSURE_DEDUPE_WINDOW_MILLIS),
		).toBe(true)
	})
})
