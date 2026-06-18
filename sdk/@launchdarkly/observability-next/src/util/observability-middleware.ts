import { cookies } from 'next/headers'
import { NextResponse } from 'next/server'

/**
 * Next.js middleware helper that forwards the browser session id (stored in the
 * `sessionSecureID` cookie by {@link LDObservabilityInit}) to your server as the
 * `x-highlight-request` header, so backend traces can be linked to the session.
 *
 * Returns a {@link NextResponse} that carries the forwarded header through to
 * downstream route handlers — Next.js only propagates request-header mutations
 * when they are passed via `NextResponse.next({ request: { headers } })`, so
 * your `middleware` must return this response:
 *
 * ```ts
 * export async function middleware(request: Request) {
 * 	return observabilityMiddleware(request)
 * }
 * ```
 *
 * Safe to run in the edge runtime — it only touches cookies and headers.
 */
export async function observabilityMiddleware(request: Request) {
	const requestHeaders = new Headers(request.headers)
	const sessionSecureID = (await cookies()).get('sessionSecureID')?.value
	const xHighlightRequest = requestHeaders.get('x-highlight-request')
	if (!xHighlightRequest && sessionSecureID) {
		requestHeaders.set('x-highlight-request', `${sessionSecureID}/`)
	}
	return NextResponse.next({ request: { headers: requestHeaders } })
}
