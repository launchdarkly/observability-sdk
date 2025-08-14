import { Attributes } from '@opentelemetry/api'
import { ErrorUtils } from 'react-native'
import type { ObservabilityClient } from '../client/ObservabilityClient'
import {
	ErrorHandlingConfig,
	ErrorDeduplicator,
	ErrorType,
	ErrorSource,
} from './errorTypes'
import {
	formatError,
	extractReactErrorInfo,
	parseConsoleArgs,
	isNetworkError,
	shouldSampleError,
} from './errorUtils'

export class ErrorInstrumentation {
	private client: ObservabilityClient
	private config: Required<ErrorHandlingConfig>
	private deduplicator: ErrorDeduplicator
	private originalHandlers: {
		globalHandler?: (error: any, isFatal?: boolean) => void
		consoleError?: (...args: any[]) => void
		consoleWarn?: (...args: any[]) => void
		unhandledRejection?: (event: any) => void
	} = {}
	private isInitialized = false

	constructor(client: ObservabilityClient, config: ErrorHandlingConfig = {}) {
		this.client = client
		this.config = {
			captureUnhandledExceptions: config.captureUnhandledExceptions ?? true,
			captureUnhandledRejections: config.captureUnhandledRejections ?? true,
			captureConsoleErrors: config.captureConsoleErrors ?? true,
			errorSampleRate: config.errorSampleRate ?? 1.0,
			beforeSend: config.beforeSend,
		}
		this.deduplicator = new ErrorDeduplicator()
	}

	public initialize(): void {
		if (this.isInitialized) {
			return
		}

		try {
			this.setupUnhandledExceptionHandler()
			this.setupUnhandledRejectionHandler()
			this.setupConsoleErrorHandler()
			this.isInitialized = true
			this.client._log('ErrorInstrumentation initialized')
		} catch (error) {
			console.error('Failed to initialize ErrorInstrumentation:', error)
		}
	}

	public destroy(): void {
		if (!this.isInitialized) {
			return
		}

		try {
			this.restoreUnhandledExceptionHandler()
			this.restoreUnhandledRejectionHandler()
			this.restoreConsoleHandlers()
			this.isInitialized = false
			this.client._log('ErrorInstrumentation destroyed')
		} catch (error) {
			console.error('Failed to destroy ErrorInstrumentation:', error)
		}
	}

	private setupUnhandledExceptionHandler(): void {
		if (!this.config.captureUnhandledExceptions) {
			return
		}

		// Store original handler
		this.originalHandlers.globalHandler = ErrorUtils.getGlobalHandler()

		// Set up our handler
		ErrorUtils.setGlobalHandler((error: any, isFatal?: boolean) => {
			this.handleUnhandledException(error, isFatal ?? false)

			// Call original handler if it exists
			if (this.originalHandlers.globalHandler) {
				this.originalHandlers.globalHandler(error, isFatal)
			}
		})
	}

	private setupUnhandledRejectionHandler(): void {
		if (!this.config.captureUnhandledRejections) {
			return
		}

		const handler = (event: any) => {
			this.handleUnhandledRejection(event)
		}

		// Try to set up unhandled rejection handler
		try {
			// React Native doesn't have native support for unhandledrejection events,
			// but we can monkey-patch Promise to catch them
			this.patchPromiseRejection()
		} catch (error) {
			console.warn('Could not setup unhandled rejection handler:', error)
		}
	}

	private setupConsoleErrorHandler(): void {
		if (!this.config.captureConsoleErrors) {
			return
		}

		// Store original console methods
		this.originalHandlers.consoleError = console.error
		this.originalHandlers.consoleWarn = console.warn

		// Patch console.error
		console.error = (...args: any[]) => {
			this.handleConsoleError('error', args)
			if (this.originalHandlers.consoleError) {
				this.originalHandlers.consoleError.apply(console, args)
			}
		}

		// Optionally patch console.warn for warnings
		console.warn = (...args: any[]) => {
			this.handleConsoleError('warn', args)
			if (this.originalHandlers.consoleWarn) {
				this.originalHandlers.consoleWarn.apply(console, args)
			}
		}
	}

	private patchPromiseRejection(): void {
		// Store original Promise methods
		const originalThen = Promise.prototype.then
		const originalCatch = Promise.prototype.catch

		// Patch Promise.prototype.then to catch unhandled rejections
		Promise.prototype.then = function(onFulfilled, onRejected) {
			return originalThen.call(
				this,
				onFulfilled,
				onRejected ||
					((reason: any) => {
						// If no rejection handler is provided, treat as unhandled
						setTimeout(() => {
							if (this instanceof Promise) {
								const event = {
									promise: this,
									reason,
									preventDefault: () => {},
								}
								this.handleUnhandledRejection?.(event)
							}
						}, 0)
						throw reason
					}),
			)
		}.bind(this)
	}

	private handleUnhandledException(error: any, isFatal: boolean): void {
		try {
			if (!shouldSampleError(this.config.errorSampleRate)) {
				return
			}

			const errorObj = error instanceof Error ? error : new Error(String(error))

			if (!this.deduplicator.shouldReport(errorObj)) {
				return
			}

			// Skip network errors as they're handled by network instrumentation
			if (isNetworkError(errorObj)) {
				return
			}

			// Apply beforeSend filter
			const filteredError = this.config.beforeSend
				? this.config.beforeSend(errorObj, {
						type: 'unhandled_exception',
						source: 'javascript',
						fatal: isFatal,
						timestamp: Date.now(),
					})
				: errorObj

			if (!filteredError) {
				return
			}

			const reactInfo = extractReactErrorInfo(error)
			const formattedError = formatError(
				filteredError,
				'unhandled_exception',
				'javascript',
				isFatal,
				reactInfo.componentStack,
			)

			// Add additional attributes
			const attributes: Attributes = {
				...formattedError.attributes,
				'error.unhandled': true,
				'error.caught_by': 'ErrorUtils.setGlobalHandler',
			}

			if (reactInfo.errorBoundaryFound) {
				attributes['react.error_boundary'] = reactInfo.errorBoundary || 'unknown'
			}

			this.client.consumeCustomError(filteredError, attributes)
		} catch (instrumentationError) {
			console.warn('Error in unhandled exception instrumentation:', instrumentationError)
		}
	}

	private handleUnhandledRejection(event: any): void {
		try {
			if (!shouldSampleError(this.config.errorSampleRate)) {
				return
			}

			const reason = event.reason || event
			const errorObj = reason instanceof Error ? reason : new Error(String(reason))

			if (!this.deduplicator.shouldReport(errorObj)) {
				return
			}

			// Skip network errors
			if (isNetworkError(errorObj)) {
				return
			}

			// Apply beforeSend filter
			const filteredError = this.config.beforeSend
				? this.config.beforeSend(errorObj, {
						type: 'unhandled_rejection',
						source: 'javascript',
						fatal: false,
						timestamp: Date.now(),
					})
				: errorObj

			if (!filteredError) {
				return
			}

			const formattedError = formatError(
				filteredError,
				'unhandled_rejection',
				'javascript',
				false,
			)

			const attributes: Attributes = {
				...formattedError.attributes,
				'error.unhandled': true,
				'error.caught_by': 'unhandledrejection',
				'promise.handled': false,
			}

			this.client.consumeCustomError(filteredError, attributes)
		} catch (instrumentationError) {
			console.warn('Error in unhandled rejection instrumentation:', instrumentationError)
		}
	}

	private handleConsoleError(level: 'error' | 'warn', args: any[]): void {
		try {
			if (!shouldSampleError(this.config.errorSampleRate)) {
				return
			}

			// Convert console arguments to error message
			const message = parseConsoleArgs(args)
			
			// Only capture if it looks like an actual error
			if (!message || message.length === 0) {
				return
			}

			// Create error from console message
			const errorObj = new Error(message)
			errorObj.name = level === 'error' ? 'ConsoleError' : 'ConsoleWarning'

			if (!this.deduplicator.shouldReport(errorObj)) {
				return
			}

			// Apply beforeSend filter
			const filteredError = this.config.beforeSend
				? this.config.beforeSend(errorObj, {
						type: 'console_error',
						source: 'javascript',
						fatal: false,
						timestamp: Date.now(),
					})
				: errorObj

			if (!filteredError) {
				return
			}

			const formattedError = formatError(
				filteredError,
				'console_error',
				'javascript',
				false,
			)

			const attributes: Attributes = {
				...formattedError.attributes,
				'error.unhandled': false,
				'error.caught_by': `console.${level}`,
				'console.level': level,
				'console.args_count': args.length,
			}

			// Only report console.error by default, console.warn is optional
			if (level === 'error') {
				this.client.consumeCustomError(filteredError, attributes)
			}
		} catch (instrumentationError) {
			console.warn('Error in console error instrumentation:', instrumentationError)
		}
	}

	private restoreUnhandledExceptionHandler(): void {
		if (this.originalHandlers.globalHandler) {
			ErrorUtils.setGlobalHandler(this.originalHandlers.globalHandler)
		}
	}

	private restoreUnhandledRejectionHandler(): void {
		// Restoring Promise patches would be complex and potentially dangerous
		// In practice, this is rarely needed as the SDK typically lives for the app lifetime
	}

	private restoreConsoleHandlers(): void {
		if (this.originalHandlers.consoleError) {
			console.error = this.originalHandlers.consoleError
		}
		if (this.originalHandlers.consoleWarn) {
			console.warn = this.originalHandlers.consoleWarn
		}
	}
}