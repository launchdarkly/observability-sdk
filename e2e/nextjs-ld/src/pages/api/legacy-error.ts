import type { NextApiRequest, NextApiResponse } from 'next'

import { PageRouterObservability } from '@launchdarkly/observability-next/server'

import { CONSTANTS } from '@/constants'

const handler = PageRouterObservability({
	sdkKey: CONSTANTS.LAUNCHDARKLY_SDK_KEY,
	serviceName: CONSTANTS.BACKEND_SERVICE_NAME,
	environment: CONSTANTS.OBSERVE_ENVIRONMENT,
})(async function fail(req: NextApiRequest, res: NextApiResponse) {
	console.warn('[pages] /api/legacy-error about to throw')
	throw new Error('Error: /api/legacy-error (Pages Router)')
})

export default handler
