import { AppRouterObservability } from '@launchdarkly/observability-next/server'

import { CONSTANTS } from '@/constants'

const withObservability = AppRouterObservability({
	sdkKey: CONSTANTS.LAUNCHDARKLY_SDK_KEY,
	serviceName: CONSTANTS.BACKEND_SERVICE_NAME,
	environment: CONSTANTS.OBSERVE_ENVIRONMENT,
})

/** POST echo — records the request body (allowed by networkRecording) so the
 * LaunchDarkly trace shows request/response details for the span. */
export const POST = withObservability(async function POST(request: Request) {
	let body: unknown = null
	try {
		body = await request.json()
	} catch {
		body = '<non-json body>'
	}

	console.info('API /api/echo echoing body', {
		body: JSON.stringify(body)?.slice(0, 200),
	})

	return Response.json({
		method: 'POST',
		echo: body,
		sessionTraceHeaders: {
			'x-highlight-request': request.headers.get('x-highlight-request'),
		},
	})
})

export const runtime = 'nodejs'
