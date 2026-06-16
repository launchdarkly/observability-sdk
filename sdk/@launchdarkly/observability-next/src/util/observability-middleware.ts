import { cookies } from 'next/headers'

/**
 * Next.js middleware helper that forwards the browser session id (stored in the
 * `sessionSecureID` cookie by {@link LDObservabilityInit}) to your server as the
 * `x-highlight-request` header, so backend traces can be linked to the session.
 *
 * Safe to run in the edge runtime — it only touches cookies and headers.
 */
export async function observabilityMiddleware(request: Request) {
	const sessionSecureID = (await cookies()).get('sessionSecureID')?.value
	const xHighlightRequest = request.headers.get('x-highlight-request')
	if (!xHighlightRequest && sessionSecureID) {
		request.headers.set('x-highlight-request', `${sessionSecureID}/`)
	}
}
