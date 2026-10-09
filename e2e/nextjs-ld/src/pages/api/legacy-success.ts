import type { NextApiRequest, NextApiResponse } from 'next'

import { PageRouterObservability } from '@launchdarkly/observability-next/server'

import { CONSTANTS } from '@/constants'

// Pages Router API routes in Next.js must live under pages/api/** — the typegen
// then validates them as ApiRouteConfig. This one is wrapped in a LaunchDarkly
// trace (`GET - /api/legacy-success`), with the browser session forwarded from
// the x-highlight-request header set by middleware.
const handler = PageRouterObservability({
	sdkKey: CONSTANTS.LAUNCHDARKLY_SDK_KEY,
	serviceName: CONSTANTS.BACKEND_SERVICE_NAME,
	environment: CONSTANTS.OBSERVE_ENVIRONMENT,
})(async function success(req: NextApiRequest, res: NextApiResponse) {
	console.info('[pages] /api/legacy-success called', { method: req.method })
	res.status(200).json({ message: 'Success: /api/legacy-success' })
})

export default handler
