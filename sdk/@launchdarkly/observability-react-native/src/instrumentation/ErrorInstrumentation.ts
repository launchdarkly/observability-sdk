import { Attributes } from '@opentelemetry/api'
import type { ErrorUtils } from 'react-native'
import type { ObservabilityClient } from '../client/ObservabilityClient'
import { ErrorDeduplicator, ErrorType, ErrorSource } from './errorTypes'
import {
	formatError,
	extractReactErrorInfo,
	parseConsoleArgs,
	isNetworkError,
} from './errorUtils'

export class ErrorInstrumentation {
	private client: ObservabilityClient
	private deduplicator: ErrorDeduplicator
	private originalHandlers: {
		globalHandler?: (error: any, isFatal?: boolean) => void
		consoleError?: (...args: any[]) => void
		consoleWarn?: (...args: any[]) => void
		unhandledRejection?: (event: any) => void
	} = {}
	private isInitialized = false

	constructor(client: ObservabilityClient) {
		this.client = client
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
		const originalPromiseConstructor = global.Promise

		// Store instance reference for use in the patched function
		const instance = this

		// Install global unhandled rejection listener
		if (global.process && typeof global.process.on === 'function') {
			// Node.js style
			global.process.on('unhandledRejection', (reason, promise) => {
				const event = {
					promise,
					reason,
					preventDefault: () => {},
				}
				instance.handleUnhandledRejection(event)
			})
		} else if (typeof global.addEventListener === 'function') {
			// Browser style
			global.addEventListener('unhandledrejection', (event) => {
				instance.handleUnhandledRejection(event)
			})
		}

		// Add an error handler for setTimeout/setInterval
		const originalSetTimeout = global.setTimeout

		// Wrap setTimeout to catch errors in callbacks but preserve the original types
		const wrappedSetTimeout = function (
			callback: Parameters<typeof originalSetTimeout>[0],
			delay?: Parameters<typeof originalSetTimeout>[1],
			...args: any[]
		): ReturnType<typeof originalSetTimeout> {
			let wrappedCallback = callback
			if (typeof callback === 'function') {
				wrappedCallback = function (this: any) {
					try {
						return (callback as Function).apply(
							this,
							arguments as unknown as any[],
						)
					} catch (error) {
						instance.handleUnhandledException(error, false)
						throw error // Re-throw to maintain original behavior
					}
				}
			}
			return originalSetTimeout(wrappedCallback as any, delay, ...args)
		}

		// Preserve all properties of setTimeout
		Object.defineProperties(
			wrappedSetTimeout,
			Object.getOwnPropertyDescriptors(originalSetTimeout),
		)

		// Apply the wrapped function
		global.setTimeout = wrappedSetTimeout as typeof global.setTimeout

		// Patch Promise.prototype.then to catch unhandled rejections
		Promise.prototype.then = function <TResult1 = any, TResult2 = never>(
			onFulfilled?:
				| ((value: any) => TResult1 | PromiseLike<TResult1>)
				| null
				| undefined,
			onRejected?:
				| ((reason: any) => TResult2 | PromiseLike<TResult2>)
				| null
				| undefined,
		): Promise<TResult1 | TResult2> {
			return originalThen.call(
				this,
				onFulfilled,
				onRejected ||
					((reason: any): TResult2 | PromiseLike<TResult2> => {
						// If no rejection handler is provided, treat as unhandled
						setTimeout(() => {
							const promiseThis = this
							if (promiseThis instanceof Promise) {
								const event = {
									promise: promiseThis,
									reason,
									preventDefault: () => {},
								}
								instance.handleUnhandledRejection(event)
							}
						}, 0)
						throw reason
					}),
			) as Promise<TResult1 | TResult2>
		}

		// Also patch Promise.catch to ensure we catch unhandled rejections
		Promise.prototype.catch = function <T = never>(
			onRejected?:
				| ((reason: any) => T | PromiseLike<T>)
				| null
				| undefined,
		): Promise<any> {
			return originalCatch.call(
				this,
				onRejected ||
					((reason: any): T | PromiseLike<T> => {
						setTimeout(() => {
							instance.handleUnhandledRejection({
								promise: this,
								reason,
								preventDefault: () => {},
							})
						}, 0)
						throw reason
					}),
			)
		}
	}

	private handleUnhandledException(error: any, isFatal: boolean): void {
		try {
			const errorObj =
				error instanceof Error ? error : new Error(String(error))

			if (!this.deduplicator.shouldReport(errorObj)) {
				return
			}

			// Skip network errors as they're handled by network instrumentation
			if (isNetworkError(errorObj)) {
				return
			}

			const reactInfo = extractReactErrorInfo(error)
			const formattedError = formatError(
				errorObj,
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
				attributes['react.error_boundary'] =
					reactInfo.errorBoundary || 'unknown'
			}

			this.client.consumeCustomError(errorObj, attributes)
		} catch (instrumentationError) {
			console.warn(
				'Error in unhandled exception instrumentation:',
				instrumentationError,
			)
		}
	}

	private handleUnhandledRejection(event: any): void {
		try {
			const reason = event.reason || event
			const errorObj =
				reason instanceof Error ? reason : new Error(String(reason))

			if (!this.deduplicator.shouldReport(errorObj)) {
				return
			}

			// Skip network errors
			if (isNetworkError(errorObj)) {
				return
			}

			const formattedError = formatError(
				errorObj,
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

			this.client.consumeCustomError(errorObj, attributes)
		} catch (instrumentationError) {
			console.warn(
				'Error in unhandled rejection instrumentation:',
				instrumentationError,
			)
		}
	}

	private handleConsoleError(level: 'error' | 'warn', args: any[]): void {
		try {
			// Convert console arguments to error message
			const message = parseConsoleArgs(args)

			// Only capture if it looks like an actual error
			if (!message || message.length === 0) {
				return
			}

			// Create error from console message
			const errorObj = new Error(message)
			errorObj.name =
				level === 'error' ? 'ConsoleError' : 'ConsoleWarning'

			if (!this.deduplicator.shouldReport(errorObj)) {
				return
			}

			const formattedError = formatError(
				errorObj,
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
				this.client.consumeCustomError(errorObj, attributes)
			}
		} catch (instrumentationError) {
			console.warn(
				'Error in console error instrumentation:',
				instrumentationError,
			)
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
