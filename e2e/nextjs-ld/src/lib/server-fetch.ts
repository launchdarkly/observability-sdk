import { cookies, headers } from 'next/headers'

import { SERVER_ORIGIN } from '@/constants'

/**
 * fetch() helper for server components / route handlers that calls this app's
 * own API routes with the browser context forwarded:
 *
 *  - the session cookie (so `await cookies()` in the API route sees the caller)
 *  - the x-highlight-request header (so the nested handler's trace is linked
 *    to the same browser session that triggered the page render)
 *
 * The OpenTelemetry node instrumentation records the outgoing request as a
 * nested span either way; the header propagation is what ties both spans to
 * the same session in LaunchDarkly.
 */
export async function serverFetch(
	path: string,
	init: RequestInit = {},
): Promise<Response> {
	const requestHeaders = new Headers(init.headers)

	const cookieHeader = (await cookies()).toString()
	if (cookieHeader && !requestHeaders.has('cookie')) {
		requestHeaders.set('cookie', cookieHeader)
	}

	const xHighlightRequest = (await headers()).get('x-highlight-request')
	if (xHighlightRequest && !requestHeaders.has('x-highlight-request')) {
		requestHeaders.set('x-highlight-request', xHighlightRequest)
	}

	return fetch(new URL(path, SERVER_ORIGIN), {
		...init,
		headers: requestHeaders,
		cache: 'no-store',
	})
}
