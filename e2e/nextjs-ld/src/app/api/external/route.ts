import { AppRouterObservability } from '@launchdarkly/observability-next/server'

import { CONSTANTS } from '@/constants'

const withObservability = AppRouterObservability({
	sdkKey: CONSTANTS.LAUNCHDARKLY_SDK_KEY,
	serviceName: CONSTANTS.BACKEND_SERVICE_NAME,
	environment: CONSTANTS.OBSERVE_ENVIRONMENT,
})

/**
 * Proxies a request to an external service. With the default
 * LAUNCHDARKLY_OTEL_NODE_ENABLE_OUTGOING_HTTP_INSTRUMENTATION, the outbound
 * http/undici call shows up as a child span of the route handler trace.
 */
export const GET = withObservability(async function GET() {
	console.info('API /api/external calling httpbingo.org')

	const res = await fetch('https://httpbingo.org/get', {
		// The OTel instrumentation propagates traceparent headers on this
		// outbound request automatically.
		headers: { 'user-agent': 'nextjs-ld-demo' },
	})
	const json = (await res.json()) as { url?: string; origin?: string }

	return Response.json({
		status: res.status,
		url: json.url,
		origin: json.origin,
	})
})

export const runtime = 'nodejs'
