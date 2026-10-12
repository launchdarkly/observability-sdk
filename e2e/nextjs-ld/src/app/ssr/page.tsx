import { LDObserve } from '@launchdarkly/observability-next/server'

import { serverFetch } from '@/lib/server-fetch'
import { CONSTANTS } from '@/constants'

export const dynamic = 'force-dynamic'

/**
 * Server-side rendered page (force-dynamic): a new trace is produced on every
 * request. Demonstrates:
 *  - console.* → LaunchDarkly logs (linked to the trace)
 *  - a nested fetch to this app's API with the session header forwarded
 *  - server-side LDObserve.recordError when ?error is set
 */
export default async function SsrPage({
	searchParams,
}: {
	searchParams: Promise<{ error?: string }>
}) {
	const error = (await searchParams).error

	console.info('[ssr] rendering /ssr', { error })

	const api = await serverFetch(
		error ? '/api/test?success=false' : '/api/test?success=true',
	)
	const apiJson = (await api.json().catch(() => null)) as {
		message?: string
	} | null

	console.info('[ssr] nested /api/test responded', { status: api.status })

	if (error) {
		// Record an explicit server-side error (the dynamic render itself 500s
		// through the error boundary demo page below; this records it even when
		// the render recovers).
		LDObserve.recordError(
			new Error('SSR error from /ssr page'),
			undefined,
			undefined,
			{ 'ssr.query.error': 'true' },
		)
	}

	return (
		<div style={{ display: 'grid', gap: 8 }}>
			<h1 style={{ margin: 0 }}>SSR page</h1>
			<p>
				Rendered on the server at{' '}
				<code>{new Date().toISOString()}</code> (this changes every
				request — force-dynamic).
			</p>
			<p>
				Nested <code>/api/test</code> result:{' '}
				<code>{apiJson?.message ?? `HTTP ${api.status}`}</code>
			</p>
			<p>
				Append <code>?error=1</code> to also record a server-side error.
			</p>
		</div>
	)
}
