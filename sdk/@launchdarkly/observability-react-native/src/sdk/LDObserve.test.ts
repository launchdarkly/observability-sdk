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
	})
})
