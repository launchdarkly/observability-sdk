import { AppRouterObservability } from '@launchdarkly/observability-next/server'

import { CONSTANTS } from '@/constants'

const withObservability = AppRouterObservability({
	sdkKey: CONSTANTS.LAUNCHDARKLY_SDK_KEY,
	serviceName: CONSTANTS.BACKEND_SERVICE_NAME,
	environment: CONSTANTS.OBSERVE_ENVIRONMENT,
})

/** Sleeps for ?ms= (default 500) — used by the streaming page to build a
 * realistic span waterfall. */
export const GET = withObservability(async function GET(request: Request) {
	const msImg = new URL(request.url).searchParams.get('ms')
	const ms = Math.min(Math.max(Number(msImg) || 500, 0), 10_000)
	await new Promise((resolve) => setTimeout(resolve, ms))
	return Response.json({ slept: ms })
})

export const runtime = 'nodejs'
