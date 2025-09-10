import { Attributes } from '@opentelemetry/api'

export type ErrorType =
	| 'unhandled_exception'
	| 'unhandled_rejection'
	| 'console_error'
	| 'react_error'
export type ErrorSource = 'javascript' | 'native' | 'react'

export interface ErrorContext {
	type: ErrorType
	source: ErrorSource
	fatal: boolean
	componentStack?: string
	appState?: 'active' | 'background' | 'inactive'
	timestamp: number
	sessionId?: string
}

export interface FormattedError {
	message: string
	name: string
	stack?: string
	context: ErrorContext
	attributes: Attributes
}
