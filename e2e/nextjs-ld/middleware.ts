import { observabilityMiddleware } from '@launchdarkly/observability-next/server'

// Forwards the browser session id ('sessionSecureID' cookie, set by
// <LDObservabilityInit />) to all server routes as the x-highlight-request
// header. Backend traces and logs pick it up via LDObserve.runWithHeaders (used
// by the observability route wrappers) and get linked to the session replay.
//
// Next.js 16 renamed this file to proxy.ts — the same code works in either:
//   export default function proxy(request: Request) { ... }
// 'middleware.ts' is kept here so the example runs on Next.js >= 14.2 as well.
export async function middleware(request: Request) {
	// Return the response so the forwarded x-highlight-request header reaches
	// downstream route handlers.
	return observabilityMiddleware(request)
}

export const config = {
	// The matcher avoids /_next static assets; everything else gets the session
	// header so pages and API routes are both covered.
	matcher: '/((?!_next/static|_next/image|favicon.ico).*)',
}
