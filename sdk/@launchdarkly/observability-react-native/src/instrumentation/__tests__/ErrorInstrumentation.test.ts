import { ErrorInstrumentation } from '../ErrorInstrumentation'
import { formatError } from '../errorUtils'
import type { ObservabilityClient } from '../../client/ObservabilityClient'
import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'

// Mock React Native modules
vi.mock('react-native', () => ({
	ErrorUtils: {
		setGlobalHandler: vi.fn(),
		getGlobalHandler: vi.fn(() => null),
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

// Mock global ErrorUtils for React Native
const mockErrorUtils = {
	setGlobalHandler: vi.fn(),
	getGlobalHandler: vi.fn(() => null),
}

;(globalThis as any).ErrorUtils = mockErrorUtils

// Mock console methods
const originalConsoleError = console.error
const originalConsoleWarn = console.warn

process.on('unhandledRejection', (_reason: any) => {
	// Silently ignore - these are expected in our tests
})

describe('ErrorInstrumentation', () => {
	let mockClient: Partial<ObservabilityClient>
	let errorInstrumentation: ErrorInstrumentation

	beforeEach(() => {
		mockClient = {
			consumeCustomError: vi.fn(),
			_log: vi.fn(),
		} as any

		// Reset console mocks
		console.error = vi.fn()
		console.warn = vi.fn()

		// Reset ErrorUtils mocks
		vi.clearAllMocks()
		mockErrorUtils.setGlobalHandler.mockClear()
		mockErrorUtils.getGlobalHandler.mockClear()
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
			errorInstrumentation = new ErrorInstrumentation(
				mockClient as ObservabilityClient,
			)
			errorInstrumentation.initialize()

			expect(mockClient._log).toHaveBeenCalledWith(
				'ErrorInstrumentation initialized',
			)
		})

		it('should not initialize twice', () => {
			errorInstrumentation = new ErrorInstrumentation(
				mockClient as ObservabilityClient,
			)
			errorInstrumentation.initialize()
			errorInstrumentation.initialize()

			expect(mockClient._log).toHaveBeenCalledTimes(1)
		})
	})

	describe('unhandled exception handling', () => {
		it('should capture unhandled exceptions', () => {
			errorInstrumentation = new ErrorInstrumentation(
				mockClient as ObservabilityClient,
			)
			errorInstrumentation.initialize()

			const testError = new Error('Test unhandled exception')

			// Simulate unhandled exception
			const handler = mockErrorUtils.setGlobalHandler.mock.calls[0][0]
			handler(testError, true)

			expect(mockClient.consumeCustomError).toHaveBeenCalledWith(
				testError,
				expect.objectContaining({
					'error.unhandled': true,
					'error.caught_by': 'ErrorUtils.setGlobalHandler',
				}),
			)
		})
	})

	describe('unhandled promise rejection handling', () => {
		it('should capture unhandled promise rejections with Error objects', async () => {
			errorInstrumentation = new ErrorInstrumentation(
				mockClient as ObservabilityClient,
			)
			errorInstrumentation.initialize()

			const testError = new Error('Test promise rejection')

			// Create an unhandled promise rejection
			Promise.reject(testError)

			// Wait for the setTimeout in the instrumentation to fire
			await new Promise((resolve) => setTimeout(resolve, 20))

			expect(mockClient.consumeCustomError).toHaveBeenCalledWith(
				testError,
				expect.objectContaining({
					'error.unhandled': true,
					'error.caught_by': 'unhandledrejection',
					'promise.handled': false,
				}),
			)
		})

		it('should capture unhandled promise rejections with primitives', async () => {
			errorInstrumentation = new ErrorInstrumentation(
				mockClient as ObservabilityClient,
			)
			errorInstrumentation.initialize()

			// Create an unhandled promise rejection with a string
			Promise.reject('String rejection reason')

			// Wait for the setTimeout in the instrumentation to fire
			await new Promise((resolve) => setTimeout(resolve, 20))

			expect(mockClient.consumeCustomError).toHaveBeenCalledWith(
				expect.objectContaining({
					message:
						'Promise rejected with string: String rejection reason',
					name: 'UnhandledRejection',
				}),
				expect.objectContaining({
					'error.unhandled': true,
					'error.caught_by': 'unhandledrejection',
					'rejection.type': 'string',
					'rejection.value': 'String rejection reason',
				}),
			)
		})

		it('should capture unhandled promise rejections with objects', async () => {
			errorInstrumentation = new ErrorInstrumentation(
				mockClient as ObservabilityClient,
			)
			errorInstrumentation.initialize()

			// Create an unhandled promise rejection with an object
			Promise.reject({
				message: 'Custom error object',
				code: 'ERR_CUSTOM',
			})

			await new Promise((resolve) => setTimeout(resolve, 20))

			expect(mockClient.consumeCustomError).toHaveBeenCalledWith(
				expect.objectContaining({
					message: 'Custom error object',
					name: 'UnhandledRejection',
				}),
				expect.objectContaining({
					'error.unhandled': true,
					'error.caught_by': 'unhandledrejection',
					'rejection.message': 'Custom error object',
					'rejection.code': 'ERR_CUSTOM',
				}),
			)
		})

		it('should capture unhandled promise rejections from Promise constructor', async () => {
			errorInstrumentation = new ErrorInstrumentation(
				mockClient as ObservabilityClient,
			)
			errorInstrumentation.initialize()

			const testError = new Error('Constructor rejection')

			// Create an unhandled promise rejection using constructor
			new Promise((_resolve, reject) => {
				reject(testError)
			})

			await new Promise((resolve) => setTimeout(resolve, 20))

			expect(mockClient.consumeCustomError).toHaveBeenCalledWith(
				testError,
				expect.objectContaining({
					'error.unhandled': true,
					'error.caught_by': 'unhandledrejection',
				}),
			)
		})

		it('should NOT capture promise rejections that are handled with catch', async () => {
			errorInstrumentation = new ErrorInstrumentation(
				mockClient as ObservabilityClient,
			)
			errorInstrumentation.initialize()

			const testError = new Error('Handled rejection')

			// Create a promise rejection that is handled
			Promise.reject(testError).catch(() => {
				// Handle the error
			})

			await new Promise((resolve) => setTimeout(resolve, 10))

			expect(mockClient.consumeCustomError).not.toHaveBeenCalled()
		})

		it('should NOT capture promise rejections that are handled with then', async () => {
			errorInstrumentation = new ErrorInstrumentation(
				mockClient as ObservabilityClient,
			)
			errorInstrumentation.initialize()

			const testError = new Error('Handled rejection')

			// Create a promise rejection that is handled with then's second argument
			Promise.reject(testError).then(null, () => {
				// Handle the error
			})

			await new Promise((resolve) => setTimeout(resolve, 10))

			expect(mockClient.consumeCustomError).not.toHaveBeenCalled()
		})

		it('should capture axios-like errors with HTTP details', async () => {
			errorInstrumentation = new ErrorInstrumentation(
				mockClient as ObservabilityClient,
			)
			errorInstrumentation.initialize()

			const axiosError = new Error('Request failed with status code 404')
			;(axiosError as any).isAxiosError = true
			;(axiosError as any).response = {
				status: 404,
				statusText: 'Not Found',
				data: { message: 'Resource not found' },
			}
			;(axiosError as any).config = {
				method: 'get',
				url: 'https://api.example.com/users/123',
			}
			;(axiosError as any).code = 'ERR_BAD_REQUEST'

			Promise.reject(axiosError)

			await new Promise((resolve) => setTimeout(resolve, 20))

			expect(mockClient.consumeCustomError).toHaveBeenCalledWith(
				axiosError,
				expect.objectContaining({
					'error.unhandled': true,
					'error.caught_by': 'unhandledrejection',
					'http.is_axios_error': true,
					'http.status_code': 404,
					'http.status_text': 'Not Found',
					'http.method': 'GET',
					'http.url': 'https://api.example.com/users/123',
					'http.error_code': 'ERR_BAD_REQUEST',
				}),
			)
		})

		it('should capture rejections with null or undefined', async () => {
			errorInstrumentation = new ErrorInstrumentation(
				mockClient as ObservabilityClient,
			)
			errorInstrumentation.initialize()

			const rejectedPromise = Promise.reject(undefined)

			await new Promise((resolve) => setTimeout(resolve, 20))

			expect(mockClient.consumeCustomError).toHaveBeenCalled()
			const call = (mockClient.consumeCustomError as any).mock.calls[0]
			expect(call[0].message).toContain('Promise rejected')
			expect(call[1]).toMatchObject({
				'error.unhandled': true,
				'error.caught_by': 'unhandledrejection',
			})
		})
	})

	describe('console error handling', () => {
		it('should capture console.error calls', () => {
			errorInstrumentation = new ErrorInstrumentation(
				mockClient as ObservabilityClient,
			)
			errorInstrumentation.initialize()

			console.error('Test console error', { data: 'test' })

			expect(mockClient.consumeCustomError).toHaveBeenCalledWith(
				expect.objectContaining({
					message: 'Test console error {"data":"test"}',
					name: 'ConsoleError',
				}),
				expect.objectContaining({
					'error.unhandled': false,
					'error.caught_by': 'console.error',
					'console.level': 'error',
					'console.args_count': 2,
				}),
			)
		})

		it('should not capture empty console messages', () => {
			errorInstrumentation = new ErrorInstrumentation(
				mockClient as ObservabilityClient,
			)
			errorInstrumentation.initialize()

			console.error('')
			console.error()

			expect(mockClient.consumeCustomError).not.toHaveBeenCalled()
		})
	})

	describe('destroy', () => {
		it('should restore original handlers on destroy', () => {
			const originalHandler = vi.fn()
			mockErrorUtils.getGlobalHandler.mockReturnValue(
				originalHandler as any,
			)

			errorInstrumentation = new ErrorInstrumentation(
				mockClient as ObservabilityClient,
			)
			errorInstrumentation.initialize()
			errorInstrumentation.destroy()

			expect(mockErrorUtils.setGlobalHandler).toHaveBeenCalledWith(
				originalHandler,
			)
			expect(mockClient._log).toHaveBeenCalledWith(
				'ErrorInstrumentation destroyed',
			)
		})
	})
})

describe('formatError', () => {
	it('should format error with all context', () => {
		const error = new Error('Test error')
		error.stack = 'Error: Test error\n  at test.js:1:1'

		const formatted = formatError(
			error,
			'unhandled_exception',
			'javascript',
			true,
			'Component stack',
		)

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
		const formatted = formatError(
			'String error',
			'console_error',
			'javascript',
			false,
		)

		expect(formatted.message).toBe('String error')
		expect(formatted.name).toBe('Error')
	})
})
