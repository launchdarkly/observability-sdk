import { LDObserve } from '@launchdarkly/observability-node'

import { registerObservability } from './register-observability'
import type { ObservabilityEnv } from './types'

import type { IncomingHttpHeaders } from 'http'

export declare type HasHeaders = {
	headers: IncomingHttpHeaders
	method?: string
	url?: string
}
export declare type HasStatus = {
	readonly statusCode: number
	readonly statusMessage: string
	status: (statusCode: number) => HasStatus
	send: (body: string) => void
}
export declare type ApiHandler<T extends HasHeaders, S extends HasStatus> = (
	req: T,
	res: S,
) => unknown | Promise<unknown>

/**
 * Wraps a Pages Router API handler so that its execution runs inside a
 * LaunchDarkly trace, with the session and request linked via incoming headers.
 */
export const Highlight =
	(env: ObservabilityEnv) =>
	<T extends HasHeaders, S extends HasStatus>(
		originalHandler: ApiHandler<T, S>,
	): ApiHandler<T, S> => {
		return async (req, res) => {
			await registerObservability(env)

			return await LDObserve.runWithHeaders(
				`${req.method?.toUpperCase()} - ${req.url}`,
				req.headers,
				async () => {
					return await originalHandler(req, res)
				},
			)
		}
	}
