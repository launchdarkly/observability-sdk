import { afterAll, describe, expect, it, vi } from 'vitest'
import { ObserveSDK } from './observe'
import { LDObserve } from './LDObserve'
import { setSdkDebugLogging } from '../client/utils/sdkLogger'

// With `manualStart` the SDK is attached but not started; span callbacks
// still run (on a no-op span) so the caller's own work is never deferred.
// This describe block runs first: the "warn once" state is shared for the
// whole file, so the warning must be asserted before any other span call.
describe('LDObserve span methods while the sdk is not started', () => {
	const warnSpy = vi.spyOn(console, 'warn')

	afterAll(() => {
		setSdkDebugLogging(false)
	})

	it('runs span callbacks on a no-op span and warns once in debug mode', async () => {
		setSdkDebugLogging(true)
		const fn = vi.fn()
		const asyncFn = vi.fn(async () => 'result')

		expect(LDObserve.startSpan('test', fn)).toBeUndefined()
		expect(fn).toHaveBeenCalledOnce()
		await expect(LDObserve.startManualSpan('test', asyncFn)).resolves.toBe(
			'result',
		)
		expect(asyncFn).toHaveBeenCalledOnce()

		// One warning no matter how many span calls could not be kept.
		expect(warnSpy).toHaveBeenCalledTimes(1)
	})
})

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

	it('still buffers fire-and-forget telemetry, but never spans', () => {
		LDObserve.recordHistogram({ name: 'test', value: 1 })

		const buffered = (
			LDObserve as unknown as { _callBuffer: Array<{ method: string }> }
		)._callBuffer.map((call) => call.method)

		expect(buffered).toContain('recordHistogram')
		expect(buffered).not.toContain('startSpan')
		expect(buffered).not.toContain('startManualSpan')
	})
})

describe('LDObserve with an attached sdk that has not started', () => {
	it('still avoids buffering span callbacks', () => {
		const sdk = { start: vi.fn(), stop: vi.fn() }
		const fresh = new (
			LDObserve as unknown as {
				constructor: new () => typeof LDObserve
			}
		).constructor()
		fresh.load(sdk as unknown as ObserveSDK, { ready: false })

		const fn = vi.fn((span) => span)
		fresh.startManualSpan('test', fn)

		expect(fn).toHaveBeenCalledOnce()
		const buffered = (
			fresh as unknown as { _callBuffer: Array<{ method: string }> }
		)._callBuffer
		expect(buffered).toHaveLength(0)
	})
})
