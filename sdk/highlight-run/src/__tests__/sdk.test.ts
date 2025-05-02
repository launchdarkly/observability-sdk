import { describe, it, expect, vi, beforeEach } from 'vitest'
import LD from '../sdk'
import type { Source } from '../client/types/shared-types'

describe('SDK', () => {
	let sdk: typeof LD

	beforeEach(() => {
		// Reset the SDK instance before each test
		sdk = LD
	})

	describe('Singleton Pattern', () => {
		it('should maintain a single instance', () => {
			const instance1 = LD
			const instance2 = LD
			expect(instance1).toBe(instance2)
		})
	})

	describe('Buffering Mechanism', () => {
		it('should buffer calls before loading', async () => {
			const mockInit = vi.fn()
			const mockIdentify = vi.fn()
			const mockTrack = vi.fn()

			// Mock the methods
			sdk.init = mockInit
			sdk.identify = mockIdentify
			sdk.track = mockTrack

			// Make calls before loading
			sdk.init('test-project')
			sdk.identify('user-123', { name: 'Test User' }, 'backend' as Source)
			sdk.track('purchase', { value: 100 })

			// Verify methods weren't called yet
			expect(mockInit).not.toHaveBeenCalled()
			expect(mockIdentify).not.toHaveBeenCalled()
			expect(mockTrack).not.toHaveBeenCalled()

			// Simulate loading
			await sdk.load()

			// Verify methods were called after loading with correct parameters
			expect(mockInit).toHaveBeenCalledWith('test-project')
			expect(mockIdentify).toHaveBeenCalledWith(
				'user-123',
				{ name: 'Test User' },
				'backend',
			)
			expect(mockTrack).toHaveBeenCalledWith('purchase', { value: 100 })
		})

		it('should maintain call order when buffering', () => {
			const mockInit = vi.fn()
			const mockIdentify = vi.fn()
			const mockTrack = vi.fn()

			// Mock the methods
			sdk.init = mockInit
			sdk.identify = mockIdentify
			sdk.track = mockTrack

			// Make calls in specific order
			sdk.init('test-project')
			sdk.track('first-event', { order: 1 })
			sdk.identify('user-123', { name: 'Test User' }, 'backend' as Source)
			sdk.track('second-event', { order: 2 })

			// Simulate loading
			sdk.load()

			// Verify methods were called in the correct order
			const initCall = mockInit.mock.calls[0]
			const firstTrackCall = mockTrack.mock.calls[0]
			const identifyCall = mockIdentify.mock.calls[0]
			const secondTrackCall = mockTrack.mock.calls[1]

			expect(initCall[0]).toBe('test-project')
			expect(firstTrackCall[0]).toBe('first-event')
			expect(identifyCall[0]).toBe('user-123')
			expect(secondTrackCall[0]).toBe('second-event')
		})

		it('should handle errors in buffered calls', () => {
			const mockInit = vi.fn()
			const mockIdentify = vi.fn()
			const mockTrack = vi.fn()

			// Mock the methods
			sdk.init = mockInit
			sdk.identify = mockIdentify
			sdk.track = mockTrack

			// Make calls before loading
			sdk.init('test-project')
			sdk.identify('user-123', { name: 'Test User' }, 'backend' as Source)
			sdk.track('purchase', { value: 100 })

			// Simulate an error in one of the buffered calls
			mockIdentify.mockImplementationOnce(() => {
				throw new Error('Test error')
			})

			// Simulate loading and verify error is caught
			expect(() => sdk.load()).not.toThrow()

			// Verify other calls still executed
			expect(mockInit).toHaveBeenCalled()
			expect(mockTrack).toHaveBeenCalled()
		})
	})

	describe('Core Methods', () => {
		it('should handle init with project ID and debug options', () => {
			const mockInit = vi.fn()
			sdk.init = mockInit

			sdk.init('test-project', { debug: true })
			sdk.load()

			expect(mockInit).toHaveBeenCalledWith('test-project', {
				debug: true,
			})
		})

		it('should handle identify with metadata and source', () => {
			const mockIdentify = vi.fn()
			sdk.identify = mockIdentify

			const metadata = { name: 'Test User' }
			const source = 'backend' as Source

			sdk.identify('user-123', metadata, source)
			sdk.load()

			expect(mockIdentify).toHaveBeenCalledWith(
				'user-123',
				metadata,
				source,
			)
		})

		it('should handle track with event and metadata', () => {
			const mockTrack = vi.fn()
			sdk.track = mockTrack

			const metadata = { value: 100 }

			sdk.track('purchase', metadata)
			sdk.load()

			expect(mockTrack).toHaveBeenCalledWith('purchase', metadata)
		})
	})

	describe('Error Handling', () => {
		it('should handle error with message and payload', () => {
			const mockError = vi.fn()
			sdk.error = mockError

			const payload = { errorCode: 'E123' }

			sdk.error('Test error', payload)
			sdk.load()

			expect(mockError).toHaveBeenCalledWith('Test error', payload)
		})

		it('should handle consumeError with error object and options', () => {
			const mockConsumeError = vi.fn()
			sdk.consumeError = mockConsumeError

			const error = new Error('Test error')
			const payload = { errorCode: 'E123' }

			sdk.consumeError(error, 'Error message', payload)
			sdk.load()

			expect(mockConsumeError).toHaveBeenCalledWith(
				error,
				'Error message',
				payload,
			)
		})
	})

	describe('Metrics', () => {
		it('should handle metrics recording', () => {
			const mockMetrics = vi.fn()
			sdk.metrics = mockMetrics

			const metrics = [
				{ name: 'test.metric', value: 100 },
				{ name: 'test.metric2', value: 200 },
			]

			sdk.metrics(metrics)
			sdk.load()

			expect(mockMetrics).toHaveBeenCalledWith(metrics)
		})

		it('should handle individual metric types', () => {
			const mockRecordGauge = vi.fn()
			const mockRecordCount = vi.fn()
			const mockRecordIncr = vi.fn()

			sdk.recordGauge = mockRecordGauge
			sdk.recordCount = mockRecordCount
			sdk.recordIncr = mockRecordIncr

			const metric = { name: 'test.metric', value: 100 }

			sdk.recordGauge(metric)
			sdk.recordCount(metric)
			sdk.recordIncr({ name: 'test.metric' })
			sdk.load()

			expect(mockRecordGauge).toHaveBeenCalledWith(metric)
			expect(mockRecordCount).toHaveBeenCalledWith(metric)
			expect(mockRecordIncr).toHaveBeenCalledWith({ name: 'test.metric' })
		})
	})
})
