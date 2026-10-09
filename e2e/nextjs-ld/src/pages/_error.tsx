import type { NextPageContext } from 'next'
import NextError, { ErrorProps } from 'next/error.js'

import {
	getLDErrorInitialProps,
	pageRouterCustomErrorHandler,
} from '@launchdarkly/observability-next/ssr'

import { CONSTANTS } from '@/constants'

/**
 * Custom `_error` page: pageRouterCustomErrorHandler initializes observability
 * (when the 500 happens before any LDObservabilityInit mount) and records the
 * server-side error into LaunchDarkly.
 */
function CustomErrorComponent({
	errorMessage,
	statusCode,
}: ErrorProps & { errorMessage: string }) {
	return (
		<div style={{ display: 'grid', gap: 12 }}>
			<h1 style={{ margin: 0 }}>{statusCode} — custom error page</h1>
			<pre
				style={{ background: '#fee2e2', padding: 12, borderRadius: 8 }}
			>
				{errorMessage}
			</pre>
			<a href="/">← back to App Router home</a>
		</div>
	)
}

CustomErrorComponent.getInitialProps = getLDErrorInitialProps

export default pageRouterCustomErrorHandler(
	{
		sdkKey: CONSTANTS.LAUNCHDARKLY_CLIENT_SIDE_ID,
		serviceName: CONSTANTS.FRONTEND_SERVICE_NAME,
		environment: CONSTANTS.OBSERVE_ENVIRONMENT,
	},
	CustomErrorComponent,
)
