import { describe, it, expect, beforeEach } from 'vitest'
import { _LDObserve } from './LDObserve'
import { ObservabilityClient } from '../client/ObservabilityClient'

describe('LDObserve Buffering', () => {
	beforeEach(() => {
		_LDObserve._resetForTesting()
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

			// TODO: Figure out better way to await initialization
			await new Promise((resolve) => setTimeout(resolve, 100))

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
			expect(bufferStatus.buffer[0].method).toBe('log')

			_LDObserve._init(new ObservabilityClient('sdkKey', {}))

			// TODO: Figure out better way to await initialization
			await new Promise((resolve) => setTimeout(resolve, 100))

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

			// TODO: Figure out better way to await initialization
			await new Promise((resolve) => setTimeout(resolve, 100))

			bufferStatus = _LDObserve._getBufferStatus()
			expect(bufferStatus.bufferSize).toBe(0)
		})

		it('should return a functional no-op span when not initialized', () => {
			const spanName = 'Test span'
			const attributes = { test: 'value' }

			// Act - call startSpan before initialization
			const span = _LDObserve.startSpan(spanName, { attributes })

			// Assert - span should be functional (no errors when called)
			expect(() => {
				span.setAttribute('key', 'value')
				span.setAttributes({ multiple: 'attributes' })
				span.addEvent('test-event')
				span.setStatus({ code: 1 })
				span.updateName('new-name')
				span.recordException(new Error('test'))
				span.end()
			}).not.toThrow()

			// Assert - span should indicate it's not recording
			expect(span.isRecording()).toBe(false)

			// Assert - span should have a valid span context
			const context = span.spanContext()
			expect(context.traceId).toBe('00000000000000000000000000000000')
			expect(context.spanId).toBe('0000000000000000')
			expect(context.traceFlags).toBe(0)
		})

		it('should handle startActiveSpan callback with no-op span when not initialized', () => {
			const spanName = 'Test active span'
			let callbackSpan: any = null

			// Act - call startActiveSpan before initialization
			const result = _LDObserve.startActiveSpan(spanName, (span) => {
				callbackSpan = span

				// These should all work without throwing
				span.setAttribute('key', 'value')
				span.setStatus({ code: 1 })
				span.end()

				return 'callback-result'
			})

			// Assert - callback should execute and return result
			expect(result).toBe('callback-result')

			// Assert - callback received a functional no-op span
			expect(callbackSpan).toBeTruthy()
			expect(callbackSpan.isRecording()).toBe(false)
		})
	})
})
