import { AppRouterObservability } from '@launchdarkly/observability-next/server'

import { CONSTANTS } from '@/constants'
import { serverFetch } from '@/lib/server-fetch'

/**
 * Route handler wrapped in a trace. The wrapper:
 *  - initializes observability if instrumentation.ts has not run yet
 *  - names the span `GET - /api/test`
 *  - links the trace to the browser session using the x-highlight-request
 *    header forwarded by middleware.ts
 */
const withObservability = AppRouterObservability({
	sdkKey: CONSTANTS.LAUNCHDARKLY_SDK_KEY,
	serviceName: CONSTANTS.BACKEND_SERVICE_NAME,
	environment: CONSTANTS.OBSERVE_ENVIRONMENT,
})

export const GET = withObservability(async function GET(request: Request) {
	const { searchParams } = new URL(request.url)
	const success = searchParams.get('success') !== 'false'

	// Server-side console recording: this becomes a LaunchDarkly log record
	// attached to the active trace.
	console.info('API /api/test called', { success })

	// A child fetch to this same app (with the session header forwarded) shows a
	// nested span in the trace waterfall.
	const echoed = await serverFetch('/api/echo', {
		method: 'POST',
		body: JSON.stringify({ calledBy: '/api/test' }),
		headers: { 'content-type': 'application/json' },
	})
	console.info('nested echo status', { status: echoed.status })

	if (!success) {
		throw new Error('Error: /api/test (App Router)')
	}

	return Response.json({ message: 'Success: /api/test' })
})

export const runtime = 'nodejs'
