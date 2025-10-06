import { Attributes } from '@opentelemetry/api'
import type { ObservabilityClient } from '../client/ObservabilityClient'
import {
	extractReactErrorInfo,
	extractRejectionDetails,
	formatError,
	parseConsoleArgs,
} from './errorUtils'

export class ErrorInstrumentation {
	private client: ObservabilityClient
	private originalHandlers: {
		globalHandler?: (error: any, isFatal?: boolean) => void
		consoleError?: (...args: any[]) => void
		consoleWarn?: (...args: any[]) => void
		unhandledRejection?: (event: any) => void
	} = {}
	private isInitialized = false
	private originalPromiseThen?: typeof Promise.prototype.then
	private unhandledRejections: Set<Promise<any>> = new Set()

	constructor(client: ObservabilityClient) {
		this.client = client
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
			this.restorePromiseRejectionHandler()
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
		this.originalHandlers.consoleError = console.error
		this.originalHandlers.consoleWarn = console.warn

		console.error = (...args: any[]) => {
			this.handleConsoleError('error', args)
			if (this.originalHandlers.consoleError) {
				this.originalHandlers.consoleError.apply(console, args)
			}
		}
	}

	private patchPromiseRejection(): void {
		// Custom promise rejection tracking implementation
		// We patch Promise.prototype.then to track rejections and detect when they're handled

		// Store the original Promise.prototype.then
		this.originalPromiseThen = Promise.prototype.then

		const self = this
		const originalThen = this.originalPromiseThen

		// Patch Promise.prototype.then to track rejection handlers
		Promise.prototype.then = function <TResult1 = any, TResult2 = never>(
			this: Promise<any>,
			onFulfilled?:
				| ((value: any) => TResult1 | PromiseLike<TResult1>)
				| null
				| undefined,
			onRejected?:
				| ((reason: any) => TResult2 | PromiseLike<TResult2>)
				| null
				| undefined,
		): Promise<TResult1 | TResult2> {
			const thisPromise = this

			// If this promise has a rejection handler, remove it from unhandled set
			if (onRejected) {
				self.unhandledRejections.delete(thisPromise)
			}

			// Call the original then with a wrapped onRejected to track handling
			const wrappedOnRejected = onRejected
				? function (reason: any) {
						// This rejection is being handled
						self.unhandledRejections.delete(thisPromise)
						return onRejected(reason)
					}
				: undefined

			// Call original then
			const resultPromise = originalThen.call(
				thisPromise,
				onFulfilled,
				wrappedOnRejected,
			) as Promise<TResult1 | TResult2>

			// If the result promise rejects, we need to track it too
			originalThen.call(resultPromise, undefined, function (reason: any) {
				// Mark this promise as potentially unhandled
				self.unhandledRejections.add(resultPromise)

				// Check after a microtask if it's still unhandled
				setTimeout(() => {
					if (self.unhandledRejections.has(resultPromise)) {
						self.unhandledRejections.delete(resultPromise)
						self.handleUnhandledRejection({ reason })
					}
				}, 0)

				// Re-throw to preserve rejection
				throw reason
			})

			return resultPromise
		} as any // Type assertion needed due to Promise patching

		// Also need to track Promise.prototype.catch
		const originalCatch = Promise.prototype.catch
		Promise.prototype.catch = function (onRejected) {
			// Remove from unhandled set when catch is added
			self.unhandledRejections.delete(this)
			return originalCatch.call(this, onRejected)
		}

		// Track rejections from Promise.reject
		const originalReject = Promise.reject
		Promise.reject = function <T = never>(reason?: any): Promise<T> {
			const promise = originalReject.call(this, reason) as Promise<T>

			// Mark as potentially unhandled
			self.unhandledRejections.add(promise)

			// Check after a microtask if it's still unhandled
			setTimeout(() => {
				if (self.unhandledRejections.has(promise)) {
					self.unhandledRejections.delete(promise)
					self.handleUnhandledRejection({ reason })
				}
			}, 0)

			return promise
		}

		// Track rejections from new Promise((resolve, reject) => reject(...))
		const OriginalPromise = Promise
		const PromiseConstructor = function (
			this: any,
			executor: (
				resolve: (value?: any) => void,
				reject: (reason?: any) => void,
			) => void,
		) {
			const promise = new OriginalPromise((resolve, reject) => {
				const wrappedReject = (reason?: any) => {
					// Mark as potentially unhandled
					self.unhandledRejections.add(promise)

					// Check after a microtask if it's still unhandled
					setTimeout(() => {
						if (self.unhandledRejections.has(promise)) {
							self.unhandledRejections.delete(promise)
							self.handleUnhandledRejection({ reason })
						}
					}, 0)

					reject(reason)
				}

				try {
					executor(resolve, wrappedReject)
				} catch (error) {
					wrappedReject(error)
				}
			})

			return promise
		}

		// Copy static methods
		Object.setPrototypeOf(PromiseConstructor, OriginalPromise)
		PromiseConstructor.prototype = OriginalPromise.prototype

		// Replace global Promise (with type assertion to handle constructor replacement)
		;(global as any).Promise = PromiseConstructor
	}

	private handleUnhandledException(error: any, isFatal: boolean): void {
		try {
			const errorObj =
				error instanceof Error ? error : new Error(String(error))

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

			console.log('::: event:', event)
			const { error: errorObj, attributes: rejectionAttributes } =
				extractRejectionDetails(reason)

			const formattedError = formatError(
				errorObj,
				'unhandled_rejection',
				'javascript',
				false,
			)

			const attributes: Attributes = {
				...formattedError.attributes,
				...rejectionAttributes, // Add extracted rejection details
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

	private restorePromiseRejectionHandler(): void {
		// Restore original Promise.prototype.then if we patched it
		if (this.originalPromiseThen) {
			Promise.prototype.then = this.originalPromiseThen
		}

		// Clear any tracked unhandled rejections
		this.unhandledRejections.clear()
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
