import { registerObservability } from '@launchdarkly/observability-next/server'
import { LDObserve } from '@launchdarkly/observability-next/server'

import { CONSTANTS } from '@/constants'

// Vercel-style manual edge runtime route. The edge runtime cannot run the
// OTel node SDK, so this handler shows the supported surface: a no-op
// registration plus a clear error for LDObserve usage.
export const GET = async () => {
	await registerObservability({
		sdkKey: CONSTANTS.LAUNCHDARKLY_SDK_KEY,
	})
	try {
		LDObserve.recordError(new Error('edge demo'), undefined, undefined)
	} catch (e) {
		return Response.json({ ok: true, note: String(e) })
	}
	return Response.json({
		ok: true,
		note: 'LDObserve.recordError did not throw (stub)',
	})
}

export const runtime = 'edge'
