import { Suspense } from 'react'

import { serverFetch } from '@/lib/server-fetch'

// The streams fetch this app's own API routes; that requires a running server,
// so disable prerendering.
export const dynamic = 'force-dynamic'

async function SlowFetch({ ms, label }: { ms: number; label: string }) {
	const started = Date.now()
	// Each component fetches the slow API route. These all run on the server
	// (this is a dynamic page because of serverFetch), producing parallel child
	// request spans under the page render trace.
	const res = await serverFetch(`/api/slow?ms=${ms}`)
	const total = Date.now() - started
	const json = (await res.json().catch(() => null)) as {
		slept?: number
	} | null

	return (
		<div
			style={{
				padding: 12,
				background: 'white',
				borderRadius: 8,
				border: '1px solid #e2e8f0',
			}}
		>
			<strong>{label}</strong>
			<div>
				slept {json?.slept ?? '?'}ms, took {total}ms
			</div>
		</div>
	)
}

export default function StreamingPage() {
	return (
		<div style={{ display: 'grid', gap: 12 }}>
			<h1 style={{ margin: 0 }}>Streaming (Suspense) page</h1>
			<p>
				Three slow streams render independently. In the trace waterfall
				the three nested <code>GET /api/slow</code> requests appear as
				parallel children of the page render span, with their durations.
			</p>
			<Suspense fallback={<div>stream A loading…</div>}>
				<SlowFetch ms={600} label="Stream A (600ms)" />
			</Suspense>
			<Suspense fallback={<div>stream B loading…</div>}>
				<SlowFetch ms={1200} label="Stream B (1200ms)" />
			</Suspense>
			<Suspense fallback={<div>stream C loading…</div>}>
				<SlowFetch ms={300} label="Stream C (300ms)" />
			</Suspense>
		</div>
	)
}
