import { LDObserve } from '@launchdarkly/observability-next/server'

// Never prerender this page — it must THROW at request time (its whole purpose
// is triggering the error boundary during a real render).
export const dynamic = 'force-dynamic'

/**
 * Route that throws server-side — the app/error.tsx boundary renders and both
 * the thrown error (via the boundary) and any console.* output are reported to
 * LaunchDarkly. The page render span comes from the instrumentation-http
 * server span, so the error is linked to the trace.
 */
export default async function DangerPage() {
	console.warn('[danger] about to throw in a server component')
	LDObserve.recordCount({
		name: 'demo.danger.attempts',
		value: 1,
		tags: [{ name: 'page', value: '/danger' }],
	})

	throw new Error('Server component render error from /danger')
}
