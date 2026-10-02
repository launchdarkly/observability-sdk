import { LDObserve } from '@launchdarkly/observability-node'

import { registerObservability } from './register-observability'
import { sanitizeSpanUrl } from './sanitize-url'
import type { ObservabilityEnv } from './types'

type NextContext = {
	params: Promise<Record<string, string>>
}
type NextHandler = (request: Request, context: NextContext) => Promise<Response>

/**
 * Wraps an App Router route handler so that its execution runs inside a
 * LaunchDarkly trace, with the session and request linked via incoming headers.
 */
export function Highlight(env: ObservabilityEnv) {
	return (originalHandler: NextHandler) =>
		async (request: Request, context: NextContext): Promise<Response> => {
			await registerObservability(env)

			return await LDObserve.runWithHeaders(
				`${request.method?.toUpperCase()} - ${sanitizeSpanUrl(request.url)}`,
				// eslint-disable-next-line @typescript-eslint/no-explicit-any
				request.headers as any,
				async () => originalHandler(request, context),
			)
		}
}
