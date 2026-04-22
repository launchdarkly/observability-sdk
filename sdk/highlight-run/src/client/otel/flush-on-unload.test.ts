import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { registerFlushOnUnload } from './index'

describe('registerFlushOnUnload', () => {
	let traceExporter: { setUnloading: ReturnType<typeof vi.fn> }
	let metricExporter: { setUnloading: ReturnType<typeof vi.fn> }
	let spanProcessor: { setSkipPendingOnFlush: ReturnType<typeof vi.fn> }
	let tracerProvider: {
		forceFlush: ReturnType<typeof vi.fn<any, Promise<void>>>
	}
	let meterProvider: {
		forceFlush: ReturnType<typeof vi.fn<any, Promise<void>>>
	}
	let cleanup: () => void

	beforeEach(() => {
		traceExporter = { setUnloading: vi.fn() }
		metricExporter = { setUnloading: vi.fn() }
		spanProcessor = { setSkipPendingOnFlush: vi.fn() }
		tracerProvider = {
			forceFlush: vi
				.fn<any, Promise<void>>()
				.mockResolvedValue(undefined),
		}
		meterProvider = {
			forceFlush: vi
				.fn<any, Promise<void>>()
				.mockResolvedValue(undefined),
		}
		cleanup = registerFlushOnUnload(
			traceExporter,
			metricExporter,
			spanProcessor,
			() => ({
				tracerProvider,
				meterProvider,
			}),
		)
		setVisibility('visible')
	})

	afterEach(() => {
		cleanup()
	})

	it('flushes on visibilitychange → hidden and toggles exporters into unload mode', () => {
		setVisibility('hidden')
		document.dispatchEvent(new Event('visibilitychange'))

		expect(traceExporter.setUnloading).toHaveBeenCalledWith(true)
		expect(metricExporter.setUnloading).toHaveBeenCalledWith(true)
		expect(spanProcessor.setSkipPendingOnFlush).toHaveBeenCalledWith(true)
		expect(tracerProvider.forceFlush).toHaveBeenCalledTimes(1)
		expect(meterProvider.forceFlush).toHaveBeenCalledTimes(1)
	})

	it('resets unload mode when the tab becomes visible again (bfcache restore)', () => {
		setVisibility('hidden')
		document.dispatchEvent(new Event('visibilitychange'))
		setVisibility('visible')
		document.dispatchEvent(new Event('visibilitychange'))

		expect(traceExporter.setUnloading).toHaveBeenLastCalledWith(false)
		expect(metricExporter.setUnloading).toHaveBeenLastCalledWith(false)
		expect(spanProcessor.setSkipPendingOnFlush).toHaveBeenLastCalledWith(
			false,
		)
	})

	it('flushes on pagehide', () => {
		window.dispatchEvent(new Event('pagehide'))

		expect(traceExporter.setUnloading).toHaveBeenCalledWith(true)
		expect(metricExporter.setUnloading).toHaveBeenCalledWith(true)
		expect(tracerProvider.forceFlush).toHaveBeenCalledTimes(1)
		expect(meterProvider.forceFlush).toHaveBeenCalledTimes(1)
	})

	it('does not flush on visibilitychange → visible', () => {
		setVisibility('visible')
		document.dispatchEvent(new Event('visibilitychange'))
		expect(tracerProvider.forceFlush).not.toHaveBeenCalled()
		expect(meterProvider.forceFlush).not.toHaveBeenCalled()
	})

	it('stops listening after cleanup', () => {
		cleanup()
		cleanup = () => {}

		setVisibility('hidden')
		document.dispatchEvent(new Event('visibilitychange'))
		window.dispatchEvent(new Event('pagehide'))

		expect(tracerProvider.forceFlush).not.toHaveBeenCalled()
		expect(meterProvider.forceFlush).not.toHaveBeenCalled()
	})

	it('swallows forceFlush rejections so unload handlers never throw', () => {
		tracerProvider.forceFlush.mockRejectedValueOnce(new Error('boom'))
		expect(() => window.dispatchEvent(new Event('pagehide'))).not.toThrow()
	})
})

function setVisibility(state: 'hidden' | 'visible') {
	Object.defineProperty(document, 'visibilityState', {
		configurable: true,
		get: () => state,
	})
}
