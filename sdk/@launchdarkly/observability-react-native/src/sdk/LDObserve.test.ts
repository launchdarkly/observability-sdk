import { describe, it, expect, beforeEach, vi } from 'vitest'
import { _LDObserve } from './LDObserve'
import { ObservabilityClient } from '../client/ObservabilityClient'

describe('LDObserve Buffering', () => {
	beforeEach(() => {
		_LDObserve._resetForTesting()
		// The test environment may provide localStorage; clear any persisted
		// session so each test starts from a clean (cold-start) state rather than
		// resuming a session written by a previous test.
		try {
			;(globalThis as { localStorage?: Storage }).localStorage?.clear()
		} catch {}
	})

	describe('Method Calls Before Initialization', () => {
		it('should buffer recordError calls when not initialized', async () => {
			const error = new Error('Test error')
			const attributes = { test: 'value' }

			_LDObserve.recordError(error, attributes)

			let bufferStatus = _LDObserve._getBufferStatus()
			expect(bufferStatus.bufferSize).toBe(1)
			expect(bufferStatus.isLoaded).toBe(false)
			expect(bufferStatus.buffer[0].method).toBe('consumeCustomError')

			const client = new ObservabilityClient('sdkKey', {})
			_LDObserve._init(client)

			await vi.waitFor(
				() => expect(_LDObserve.isInitialized()).toBe(true),
				{ timeout: 2000 },
			)
			bufferStatus = _LDObserve._getBufferStatus()
			expect(bufferStatus.bufferSize).toBe(0)
		})

		it('should buffer recordLog calls when not initialized', async () => {
			const message = 'Test log message'
			const level = 'info'
			const attributes = { test: 'value' }

			_LDObserve.recordLog(message, level, attributes)

			let bufferStatus = _LDObserve._getBufferStatus()
			expect(bufferStatus.bufferSize).toBe(1)
			expect(bufferStatus.isLoaded).toBe(false)
			expect(bufferStatus.buffer[0].method).toBe('recordLog')

			_LDObserve._init(new ObservabilityClient('sdkKey', {}))

			await vi.waitFor(
				() => expect(_LDObserve.isInitialized()).toBe(true),
				{ timeout: 2000 },
			)
			bufferStatus = _LDObserve._getBufferStatus()
			expect(bufferStatus.bufferSize).toBe(0)
		})

		it('should buffer startSpan calls when not initialized', async () => {
			const spanName = 'Test span'
			const attributes = { test: 'value' }

			_LDObserve.startSpan(spanName, { attributes })

			let bufferStatus = _LDObserve._getBufferStatus()
			expect(bufferStatus.bufferSize).toBe(1)
			expect(bufferStatus.isLoaded).toBe(false)
			expect(bufferStatus.buffer[0].method).toBe('startSpan')

			_LDObserve._init(new ObservabilityClient('sdkKey', {}))

			await vi.waitFor(
				() => expect(_LDObserve.isInitialized()).toBe(true),
				{ timeout: 2000 },
			)
			bufferStatus = _LDObserve._getBufferStatus()
			expect(bufferStatus.bufferSize).toBe(0)
		})

		it('should return a functional no-op span when not initialized', () => {
			const spanName = 'Test span'
			const attributes = { test: 'value' }

			const span = _LDObserve.startSpan(spanName, { attributes })

			expect(() => {
				span.setAttribute('key', 'value')
				span.setAttributes({ multiple: 'attributes' })
				span.addEvent('test-event')
				span.setStatus({ code: 1 })
				span.updateName('new-name')
				span.recordException(new Error('test'))
				span.end()
			}).not.toThrow()

			expect(span.isRecording()).toBe(false)

			const context = span.spanContext()
			expect(context.traceId).toBe('00000000000000000000000000000000')
			expect(context.spanId).toBe('0000000000000000')
			expect(context.traceFlags).toBe(0)
		})

		it('should handle startActiveSpan callback with no-op span when not initialized', () => {
			const spanName = 'Test active span'
			let callbackSpan: any = null

			const result = _LDObserve.startActiveSpan(spanName, (span) => {
				callbackSpan = span

				span.setAttribute('key', 'value')
				span.setStatus({ code: 1 })
				span.end()

				return 'callback-result'
			})

			expect(result).toBe('callback-result')

			expect(callbackSpan).toBeTruthy()
			expect(callbackSpan.isRecording()).toBe(false)
		})

		it('exposes the session id synchronously before init completes (session replay adoption)', async () => {
			const client = new ObservabilityClient('sdkKey', {})
			_LDObserve._init(client)

			// Before async init finishes, integrations (e.g. session replay)
			// must still be able to read a stable session id to adopt it.
			const earlyId = _LDObserve.getSessionInfo().sessionId
			expect(typeof earlyId).toBe('string')
			expect(earlyId.length).toBeGreaterThan(0)

			await vi.waitFor(
				() => expect(_LDObserve.isInitialized()).toBe(true),
				{ timeout: 2000 },
			)

			// On a fresh (cold) start the id must not change once init completes.
			expect(_LDObserve.getSessionInfo().sessionId).toBe(earlyId)
		})

		it('getSessionIdWhenReady waits for init and returns the resolved session id', async () => {
			const client = new ObservabilityClient('sdkKey', {})
			_LDObserve._init(client)

			const readyId = await _LDObserve.getSessionIdWhenReady(2000)

			expect(_LDObserve.isInitialized()).toBe(true)
			expect(typeof readyId).toBe('string')
			expect(readyId).toBe(_LDObserve.getSessionInfo().sessionId)
		})

		it('getSessionIdWhenReady resolves (does not hang) when init never completes', async () => {
			// No _init(): load() is never called. The bounded wait must still resolve
			// instead of blocking session replay from starting forever.
			await expect(
				_LDObserve.getSessionIdWhenReady(50),
			).resolves.toBeUndefined()
		})

		it('stop() before load aborts the in-flight init and never loads the client', async () => {
			const client = new ObservabilityClient('sdkKey', {})
			const stopSpy = vi.spyOn(client, 'stop')
			_LDObserve._init(client)

			// Stop while init() is still in flight (buffered, not yet loaded). This
			// must reach the real client so its `stopped` guard is set, rather than
			// being swallowed by the buffer.
			await _LDObserve.stop()
			expect(stopSpy).toHaveBeenCalledTimes(1)

			// Give any lingering init() microtasks and poller ticks a chance to run;
			// the client must not come up initialized/loaded after being stopped.
			await new Promise((resolve) => setTimeout(resolve, 150))

			expect(client.getIsInitialized()).toBe(false)
			expect(_LDObserve.isInitialized()).toBe(false)
		})

		it('should upgrade getTracer() obtained before init after client loads', async () => {
			const earlyTracer = _LDObserve.getTracer()
			const client = new ObservabilityClient('sdkKey', {})
			const getTracerSpy = vi.spyOn(client, 'getTracer')

			_LDObserve._init(client)

			await vi.waitFor(
				() => {
					expect(_LDObserve.isInitialized()).toBe(true)
				},
				{ timeout: 2000 },
			)

			earlyTracer.startSpan('post-init-span')
			expect(getTracerSpy).toHaveBeenCalled()
		})
	})
})
