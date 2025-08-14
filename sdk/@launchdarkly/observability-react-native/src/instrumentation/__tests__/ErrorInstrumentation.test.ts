import { ErrorInstrumentation } from '../ErrorInstrumentation'
import { ErrorDeduplicator } from '../errorTypes'
import { formatError } from '../errorUtils'
import type { ObservabilityClient } from '../../client/ObservabilityClient'

// Mock React Native modules
jest.mock('react-native', () => ({
	ErrorUtils: {
		setGlobalHandler: jest.fn(),
		getGlobalHandler: jest.fn(() => null),
	},
	AppState: {
		currentState: 'active',
	},
	Platform: {
		OS: 'ios',
		Version: '17.0',
		constants: {
			Model: 'iPhone',
			systemName: 'iOS',
		},
	},
}))

// Mock console methods
const originalConsoleError = console.error
const originalConsoleWarn = console.warn

describe('ErrorInstrumentation', () => {
	let mockClient: jest.Mocked<ObservabilityClient>
	let errorInstrumentation: ErrorInstrumentation

	beforeEach(() => {
		mockClient = {
			consumeCustomError: jest.fn(),
			_log: jest.fn(),
		} as any

		// Reset console mocks
		console.error = jest.fn()
		console.warn = jest.fn()
	})

	afterEach(() => {
		if (errorInstrumentation) {
			errorInstrumentation.destroy()
		}
		console.error = originalConsoleError
		console.warn = originalConsoleWarn
	})

	describe('initialization', () => {
		it('should initialize with default configuration', () => {
			errorInstrumentation = new ErrorInstrumentation(mockClient)
			errorInstrumentation.initialize()

			expect(mockClient._log).toHaveBeenCalledWith('ErrorInstrumentation initialized')
		})

		it('should not initialize twice', () => {
			errorInstrumentation = new ErrorInstrumentation(mockClient)
			errorInstrumentation.initialize()
			errorInstrumentation.initialize()

			expect(mockClient._log).toHaveBeenCalledTimes(1)
		})

		it('should initialize with custom configuration', () => {
			const config = {
				captureUnhandledExceptions: false,
				captureConsoleErrors: true,
				errorSampleRate: 0.5,
			}

			errorInstrumentation = new ErrorInstrumentation(mockClient, config)
			errorInstrumentation.initialize()

			expect(mockClient._log).toHaveBeenCalledWith('ErrorInstrumentation initialized')
		})
	})

	describe('unhandled exception handling', () => {
		it('should capture unhandled exceptions', () => {
			errorInstrumentation = new ErrorInstrumentation(mockClient)
			errorInstrumentation.initialize()

			const testError = new Error('Test unhandled exception')

			// Simulate unhandled exception
			const { ErrorUtils } = require('react-native')
			const handler = ErrorUtils.setGlobalHandler.mock.calls[0][0]
			handler(testError, true)

			expect(mockClient.consumeCustomError).toHaveBeenCalledWith(
				testError,
				expect.objectContaining({
					'error.type': 'unhandled_exception',
					'error.source': 'javascript',
					'error.fatal': true,
					'error.unhandled': true,
					'error.caught_by': 'ErrorUtils.setGlobalHandler',
				}),
			)
		})

		it('should not capture when disabled', () => {
			errorInstrumentation = new ErrorInstrumentation(mockClient, {
				captureUnhandledExceptions: false,
			})
			errorInstrumentation.initialize()

			expect(require('react-native').ErrorUtils.setGlobalHandler).not.toHaveBeenCalled()
		})

		it('should respect error sampling rate', () => {
			// Mock Math.random to return 0.6
			const originalRandom = Math.random
			Math.random = jest.fn(() => 0.6)

			errorInstrumentation = new ErrorInstrumentation(mockClient, {
				errorSampleRate: 0.5, // Should reject errors > 0.5
			})
			errorInstrumentation.initialize()

			const testError = new Error('Test error')
			const { ErrorUtils } = require('react-native')
			const handler = ErrorUtils.setGlobalHandler.mock.calls[0][0]
			handler(testError, false)

			expect(mockClient.consumeCustomError).not.toHaveBeenCalled()

			Math.random = originalRandom
		})

		it('should apply beforeSend filter', () => {
			const beforeSend = jest.fn().mockReturnValue(null)

			errorInstrumentation = new ErrorInstrumentation(mockClient, {
				beforeSend,
			})
			errorInstrumentation.initialize()

			const testError = new Error('Test error')
			const { ErrorUtils } = require('react-native')
			const handler = ErrorUtils.setGlobalHandler.mock.calls[0][0]
			handler(testError, false)

			expect(beforeSend).toHaveBeenCalledWith(
				testError,
				expect.objectContaining({
					type: 'unhandled_exception',
					source: 'javascript',
					fatal: false,
				}),
			)
			expect(mockClient.consumeCustomError).not.toHaveBeenCalled()
		})
	})

	describe('console error handling', () => {
		it('should capture console.error calls', () => {
			errorInstrumentation = new ErrorInstrumentation(mockClient)
			errorInstrumentation.initialize()

			console.error('Test console error', { data: 'test' })

			expect(mockClient.consumeCustomError).toHaveBeenCalledWith(
				expect.objectContaining({
					message: 'Test console error {"data":"test"}',
					name: 'ConsoleError',
				}),
				expect.objectContaining({
					'error.type': 'console_error',
					'error.source': 'javascript',
					'error.caught_by': 'console.error',
					'console.level': 'error',
					'console.args_count': 2,
				}),
			)
		})

		it('should not capture when disabled', () => {
			errorInstrumentation = new ErrorInstrumentation(mockClient, {
				captureConsoleErrors: false,
			})
			errorInstrumentation.initialize()

			console.error('Test error')

			expect(mockClient.consumeCustomError).not.toHaveBeenCalled()
		})

		it('should not capture empty console messages', () => {
			errorInstrumentation = new ErrorInstrumentation(mockClient)
			errorInstrumentation.initialize()

			console.error('')
			console.error()

			expect(mockClient.consumeCustomError).not.toHaveBeenCalled()
		})
	})

	describe('error deduplication', () => {
		it('should deduplicate similar errors', () => {
			errorInstrumentation = new ErrorInstrumentation(mockClient)
			errorInstrumentation.initialize()

			const testError = new Error('Duplicate error')
			const { ErrorUtils } = require('react-native')
			const handler = ErrorUtils.setGlobalHandler.mock.calls[0][0]

			// First occurrence should be reported
			handler(testError, false)
			expect(mockClient.consumeCustomError).toHaveBeenCalledTimes(1)

			// Second occurrence within dedup window should be ignored
			handler(testError, false)
			expect(mockClient.consumeCustomError).toHaveBeenCalledTimes(1)
		})
	})

	describe('cleanup', () => {
		it('should restore original handlers on destroy', () => {
			const originalHandler = jest.fn()
			const { ErrorUtils } = require('react-native')
			ErrorUtils.getGlobalHandler.mockReturnValue(originalHandler)

			errorInstrumentation = new ErrorInstrumentation(mockClient)
			errorInstrumentation.initialize()
			errorInstrumentation.destroy()

			expect(ErrorUtils.setGlobalHandler).toHaveBeenCalledWith(originalHandler)
			expect(mockClient._log).toHaveBeenCalledWith('ErrorInstrumentation destroyed')
		})
	})
})

describe('ErrorDeduplicator', () => {
	let deduplicator: ErrorDeduplicator

	beforeEach(() => {
		deduplicator = new ErrorDeduplicator()
	})

	it('should allow first occurrence of error', () => {
		const error = new Error('Test error')
		expect(deduplicator.shouldReport(error)).toBe(true)
	})

	it('should block duplicate errors within time window', () => {
		const error = new Error('Test error')
		
		expect(deduplicator.shouldReport(error)).toBe(true)
		expect(deduplicator.shouldReport(error)).toBe(false)
	})

	it('should allow different errors', () => {
		const error1 = new Error('Error 1')
		const error2 = new Error('Error 2')
		
		expect(deduplicator.shouldReport(error1)).toBe(true)
		expect(deduplicator.shouldReport(error2)).toBe(true)
	})
})

describe('formatError', () => {
	it('should format error with all context', () => {
		const error = new Error('Test error')
		error.stack = 'Error: Test error\n  at test.js:1:1'

		const formatted = formatError(error, 'unhandled_exception', 'javascript', true, 'Component stack')

		expect(formatted).toEqual({
			message: 'Test error',
			name: 'Error',
			stack: 'Error: Test error\n  at test.js:1:1',
			context: expect.objectContaining({
				type: 'unhandled_exception',
				source: 'javascript',
				fatal: true,
				componentStack: 'Component stack',
				appState: 'active',
				timestamp: expect.any(Number),
			}),
			attributes: expect.objectContaining({
				'error.type': 'unhandled_exception',
				'error.source': 'javascript',
				'error.fatal': true,
				'app.state': 'active',
				'platform.os': 'ios',
				'platform.version': '17.0',
				'react.component_stack': 'Component stack',
			}),
		})
	})

	it('should handle non-Error objects', () => {
		const formatted = formatError('String error', 'console_error', 'javascript', false)

		expect(formatted.message).toBe('String error')
		expect(formatted.name).toBe('Error')
	})
})