import { describe, expect, it, vi } from 'vitest'
import { LDObserve } from './LDObserve'

// The SDK is never loaded in this file, so every call below takes the
// pre-initialization path.
describe('LDObserve span methods before initialization', () => {
	it('runs a startSpan callback passed in the options position', () => {
		const fn = vi.fn(() => 'result')

		expect(LDObserve.startSpan('test', fn)).toBe('result')
		expect(fn).toHaveBeenCalledOnce()
	})

	it('runs a startSpan callback passed after options', () => {
		const fn = vi.fn(() => 'result')

		expect(LDObserve.startSpan('test', { attributes: {} }, fn)).toBe(
			'result',
		)
		expect(fn).toHaveBeenCalledOnce()
	})

	it('runs a startManualSpan callback and hands it a usable span', () => {
		const fn = vi.fn((span) => {
			span.setAttribute('action', 'pause')
			span.end()
			return 'result'
		})

		expect(LDObserve.startManualSpan('test', fn)).toBe('result')
		expect(fn).toHaveBeenCalledOnce()
	})

	it('awaits an async startManualSpan callback', async () => {
		const fn = vi.fn(async () => 'result')

		await expect(LDObserve.startManualSpan('test', fn)).resolves.toBe(
			'result',
		)
		expect(fn).toHaveBeenCalledOnce()
	})

	it('still buffers fire-and-forget telemetry', () => {
		expect(
			LDObserve.recordHistogram({ name: 'test', value: 1 }),
		).toBeUndefined()
	})
})
