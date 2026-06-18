'use client' // Error components must be Client Components

import {
	AppRouterErrorProps,
	appRouterSsrErrorHandler,
} from '@launchdarkly/observability-next/ssr'

export default appRouterSsrErrorHandler(
	({ error, reset }: AppRouterErrorProps) => {
		console.error(error)

		return (
			<div>
				<h2>Something went wrong!</h2>
				<button onClick={() => reset()}>Try again</button>
			</div>
		)
	},
)
