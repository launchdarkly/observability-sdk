import { Attributes } from '@opentelemetry/api'
import type { ObservabilityClient } from '../client/ObservabilityClient'
import {
	extractReactErrorInfo,
	extractRejectionDetails,
	formatError,
	parseConsoleArgs,
} from './errorUtils'

// Type for HermesInternal
interface HermesInternal {
	enablePromiseRejectionTracker: (options: {
		allRejections: boolean
		onUnhandled: (id: number, error: any) => void
		onHandled: (id: number) => void
	}) => void
}

declare const HermesInternal: HermesInternal | undefined

export class ErrorInstrumentation {
	private client: ObservabilityClient
	private originalHandlers: {
		globalHandler?: (error: any, isFatal?: boolean) => void
		consoleError?: (...args: any[]) => void
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
		// Use HermesInternal if available (Hermes engine in React Native)
		if (
			typeof HermesInternal !== 'undefined' &&
			HermesInternal?.enablePromiseRejectionTracker
		) {
			try {
				HermesInternal.enablePromiseRejectionTracker({
					allRejections: true,
					onUnhandled: (id: number, error: any) => {
						this.client._log(
							`Promise rejection unhandled: ${id}`,
							error,
						)

						this.handleUnhandledRejection({ reason: error })
					},
					onHandled: (id: number) => {
						this.client._log(`Promise rejection handled: ${id}`)
					},
				})
			} catch (error) {
				this.client._log(
					'Could not setup HermesInternal rejection tracker:',
					error,
				)
			}
		}
	}

	private setupConsoleErrorHandler(): void {
		this.originalHandlers.consoleError = console.error

		console.error = (...args: any[]) => {
			this.handleConsoleError('error', args)
			if (this.originalHandlers.consoleError) {
				this.originalHandlers.consoleError.apply(console, args)
			}
		}
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

	private handleConsoleError(level: 'error', args: any[]): void {
		try {
			// Convert console arguments to error message
			const message = parseConsoleArgs(args)

			// Only capture if it looks like an actual error
			if (!message || message.length === 0) {
				return
			}

			// Create error from console message
			const errorObj = new Error(message)
			errorObj.name = 'ConsoleError'

			const formattedError = formatError(
				errorObj,
				'console_error',
				'javascript',
				false,
			)

			const attributes: Attributes = {
				...formattedError.attributes,
				'error.unhandled': false,
				'error.caught_by': 'console.error',
				'console.level': 'error',
				'console.args_count': args.length,
			}

			this.client.consumeCustomError(errorObj, attributes)
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
	}
}
