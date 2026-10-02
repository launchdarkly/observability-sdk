import { AppRouterObservability } from '@launchdarkly/observability-next/server'

import { CONSTANTS } from '@/constants'

const withObservability = AppRouterObservability({
	sdkKey: CONSTANTS.LAUNCHDARKLY_SDK_KEY,
	serviceName: 'nextjs-ld-backend',
	environment: 'e2e-test',
})

export const GET = withObservability(async function GET(request: Request) {
	const { searchParams } = new URL(request.url)
	const success = searchParams.get('success') === 'true'

	if (!success) {
		throw new Error('Error: /api/test (App Router)')
	}

	return Response.json({ message: 'Success: /api/test' })
})

export const runtime = 'nodejs'
