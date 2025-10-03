import { Attributes } from '@opentelemetry/api'
import type { ObservabilityClient } from '../client/ObservabilityClient'
import {
	extractReactErrorInfo,
	formatError,
	isNetworkError,
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
		const rejectionTrackingConfig = {
			allRejections: true,
			onUnhandled: this.handleUnhandledRejection.bind(this),
			onHandled: () => {},
		}

		// @ts-expect-error to allow for checking if HermesInternal exists on `global` since it isn't part of its type
		const hermesInternal = global?.HermesInternal

		// Do the same checking as react-native to make sure we add tracking to the right Promise implementation
		// https://github.com/facebook/react-native/blob/v0.77.0/packages/react-native/Libraries/Core/polyfillPromise.js#L25
		if (hermesInternal?.hasPromise?.()) {
			hermesInternal?.enablePromiseRejectionTracker?.(
				rejectionTrackingConfig,
			)
		} else {
			// [Unhandled Rejections](https://github.com/then/promise/blob/master/Readme.md#unhandled-rejections)
			// [promise/setimmediate/rejection-tracking](https://github.com/then/promise/blob/master/src/rejection-tracking.js)
			require('promise/setimmediate/rejection-tracking').enable(
				rejectionTrackingConfig,
			)
		}
	}

	private handleUnhandledException(error: any, isFatal: boolean): void {
		try {
			const errorObj =
				error instanceof Error ? error : new Error(String(error))

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

	private restoreConsoleHandlers(): void {
		if (this.originalHandlers.consoleError) {
			console.error = this.originalHandlers.consoleError
		}
		if (this.originalHandlers.consoleWarn) {
			console.warn = this.originalHandlers.consoleWarn
		}
	}
}
