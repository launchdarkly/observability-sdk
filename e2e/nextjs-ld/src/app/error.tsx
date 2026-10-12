'use client'

import {
	AppRouterErrorProps,
	appRouterSsrErrorHandler,
} from '@launchdarkly/observability-next/ssr'

// The SDK helper records the error (LDObserve.recordError) whenever this
// boundary renders, then renders our UI.
export default appRouterSsrErrorHandler(
	({ error, reset }: AppRouterErrorProps) => {
		console.error('app error boundary:', error)

		return (
			<div style={{ display: 'grid', gap: 12, justifyItems: 'start' }}>
				<h2>Something went wrong!</h2>
				<pre
					style={{
						background: '#fee2e2',
						padding: 12,
						borderRadius: 8,
					}}
				>
					{error.message}
				</pre>
				<button
					onClick={reset}
					style={{ padding: '8px 12px', borderRadius: 6 }}
				>
					Try again
				</button>
			</div>
		)
	},
)
