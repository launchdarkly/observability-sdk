import { Attributes } from '@opentelemetry/api'
import { AppState, Platform } from 'react-native'
import {
	ErrorContext,
	ErrorType,
	ErrorSource,
	FormattedError,
} from './errorTypes'
import type { AxiosError } from 'axios'

export function formatError(
	error: Error | any,
	type: ErrorType,
	source: ErrorSource,
	isFatal: boolean = false,
	componentStack?: string,
): FormattedError {
	// Ensure we have an Error object
	const errorObj = error instanceof Error ? error : new Error(String(error))

	const context: ErrorContext = {
		type,
		source,
		fatal: isFatal,
		componentStack,
		appState: AppState.currentState as 'active' | 'background' | 'inactive',
		timestamp: Date.now(),
	}

	const attributes: Attributes = {
		'error.type': type,
		'error.source': source,
		'error.fatal': isFatal,
		'app.state': context.appState,
		'platform.os': Platform.OS,
		'platform.version': Platform.Version,
	}

	// Add React Native specific attributes
	if (Platform.OS === 'ios') {
		attributes['platform.ios.model'] = (Platform as any).constants?.Model
		attributes['platform.ios.system_name'] = (
			Platform as any
		).constants?.systemName
	} else if (Platform.OS === 'android') {
		attributes['platform.android.brand'] = (
			Platform as any
		).constants?.Brand
		attributes['platform.android.model'] = (
			Platform as any
		).constants?.Model
		attributes['platform.android.release'] = (
			Platform as any
		).constants?.Release
	}

	// Add component stack if available
	if (componentStack) {
		attributes['react.component_stack'] = componentStack
	}

	return {
		message: errorObj.message || 'Unknown error',
		name: errorObj.name || 'Error',
		stack: errorObj.stack,
		context,
		attributes,
	}
}

export function extractReactErrorInfo(error: any): {
	componentStack?: string
	errorBoundary?: string
	errorBoundaryFound?: boolean
} {
	const info: any = {}

	// React adds componentStack to errors
	if (error.componentStack) {
		info.componentStack = error.componentStack
	}

	// Check if error was caught by an error boundary
	if (error.errorBoundary) {
		info.errorBoundary = error.errorBoundary
		info.errorBoundaryFound = true
	}

	return info
}

export function parseConsoleArgs(args: any[]): string {
	return args
		.map((arg) => {
			if (typeof arg === 'object') {
				try {
					return JSON.stringify(arg)
				} catch {
					return String(arg)
				}
			}
			return String(arg)
		})
		.join(' ')
}

/**
 * Extract detailed information from an unhandled promise rejection reason.
 * This handles various types of rejection reasons including:
 * - Error objects
 * - Axios errors
 * - Fetch errors
 * - Primitives (numbers, strings, etc.)
 * - Plain objects
 */
export function extractRejectionDetails(reason: any): {
	error: Error
	attributes: Attributes
} {
	const attributes: Attributes = {}

	if (reason instanceof Error) {
		if ('isAxiosError' in reason) {
			attributes['http.is_axios_error'] = true

			const axiosError = reason as AxiosError
			if (axiosError.response) {
				attributes['http.status_code'] = axiosError.response.status
				attributes['http.status_text'] = axiosError.response.statusText
				if (axiosError.response.data) {
					try {
						attributes['http.response_data'] =
							typeof axiosError.response.data === 'string'
								? axiosError.response.data
								: JSON.stringify(axiosError.response.data)
					} catch {
						// Ignore if response data can't be stringified
					}
				}
			}

			if (axiosError.config) {
				attributes['http.method'] =
					axiosError.config.method?.toUpperCase()
				attributes['http.url'] = axiosError.config.url
			}

			if (axiosError.code) {
				attributes['http.error_code'] = axiosError.code
			}
		}

		// Check for fetch/network error structure
		if (
			reason.name === 'TypeError' &&
			/fetch|network/i.test(reason.message)
		) {
			attributes['http.is_fetch_error'] = true
		}

		return { error: reason, attributes }
	}

	// Handle objects with error-like properties
	if (reason && typeof reason === 'object') {
		let message = 'Promise rejected with object'

		// Try to extract a meaningful message
		if (reason.message) {
			message = String(reason.message)
		} else if (reason.error) {
			message = String(reason.error)
		} else if (reason.description) {
			message = String(reason.description)
		}

		// Add all enumerable properties as attributes
		try {
			Object.keys(reason).forEach((key) => {
				const value = reason[key]
				// Skip functions and complex objects
				if (typeof value !== 'function' && typeof value !== 'object') {
					attributes[`rejection.${key}`] = value
				} else if (value && typeof value === 'object') {
					try {
						attributes[`rejection.${key}`] = JSON.stringify(value)
					} catch {
						// Skip if can't stringify
					}
				}
			})
		} catch {
			// Ignore errors while extracting properties
		}

		const error = new Error(message)
		error.name = reason.name || 'UnhandledRejection'

		// Try to preserve stack if available
		if (reason.stack) {
			error.stack = reason.stack
		}

		return { error, attributes }
	}

	// Handle primitives (numbers, strings, booleans, etc.)
	const primitiveType = typeof reason
	let message: string

	if (reason === null || reason === undefined) {
		message = `Promise rejected with ${reason}`
	} else {
		message = `Promise rejected with ${primitiveType}: ${reason}`
	}

	attributes['rejection.type'] = primitiveType
	attributes['rejection.value'] = String(reason)

	const error = new Error(message)
	error.name = 'UnhandledRejection'

	return { error, attributes }
}

export function shouldSampleError(sampleRate: number): boolean {
	return Math.random() < sampleRate
}
