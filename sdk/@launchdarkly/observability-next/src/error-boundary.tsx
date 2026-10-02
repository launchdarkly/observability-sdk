import { LDObserve } from '@launchdarkly/observability'
import * as React from 'react'

export interface ErrorBoundaryProps {
	children?: React.ReactNode
	/**
	 * Rendered when a descendant throws. Either a node or a render function that
	 * receives the captured error.
	 */
	fallback?: React.ReactNode | ((error: Error) => React.ReactNode)
}

export interface ErrorBoundaryState {
	error: Error | null
}

/**
 * A React error boundary that reports caught errors to LaunchDarkly via
 * {@link LDObserve}. Unlike the Highlight error boundary, it does not depend on
 * a global `window.H`; it talks to the standalone-initialized observability
 * plugin directly.
 */
export class ErrorBoundary extends React.Component<
	ErrorBoundaryProps,
	ErrorBoundaryState
> {
	state: ErrorBoundaryState = { error: null }

	static getDerivedStateFromError(error: Error): ErrorBoundaryState {
		return { error }
	}

	componentDidCatch(error: Error, info: React.ErrorInfo) {
		LDObserve.recordError(error, error.message, {
			'react.componentStack': info.componentStack ?? '',
		})
	}

	render() {
		const { error } = this.state
		if (error) {
			const { fallback } = this.props
			if (typeof fallback === 'function') {
				return <>{fallback(error)}</>
			}
			return <>{fallback ?? null}</>
		}
		return <>{this.props.children}</>
	}
}
