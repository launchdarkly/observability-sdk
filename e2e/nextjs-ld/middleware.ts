import { observabilityMiddleware } from '@launchdarkly/observability-next/server'

export async function middleware(request: Request) {
	// Return the response so the forwarded x-highlight-request header reaches
	// downstream route handlers.
	return observabilityMiddleware(request)
}
