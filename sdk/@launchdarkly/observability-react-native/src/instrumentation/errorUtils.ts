import { Attributes } from '@opentelemetry/api'
import { AppState, Platform } from 'react-native'
import { ErrorContext, ErrorType, ErrorSource, FormattedError } from './errorTypes'

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
		attributes['platform.ios.system_name'] = (Platform as any).constants?.systemName
	} else if (Platform.OS === 'android') {
		attributes['platform.android.brand'] = (Platform as any).constants?.Brand
		attributes['platform.android.model'] = (Platform as any).constants?.Model
		attributes['platform.android.release'] = (Platform as any).constants?.Release
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
		.map(arg => {
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

export function isNetworkError(error: Error): boolean {
	const networkErrorPatterns = [
		/network/i,
		/fetch/i,
		/XMLHttpRequest/i,
		/CORS/i,
		/ERR_NETWORK/i,
		/ERR_INTERNET_DISCONNECTED/i,
		/ERR_NAME_NOT_RESOLVED/i,
	]

	return networkErrorPatterns.some(pattern =>
		pattern.test(error.message) || pattern.test(error.name),
	)
}

export function shouldSampleError(sampleRate: number): boolean {
	return Math.random() < sampleRate
}