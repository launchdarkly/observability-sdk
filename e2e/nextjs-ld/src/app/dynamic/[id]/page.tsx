import { LDObserve } from '@launchdarkly/observability-next/server'

import { serverFetch } from '@/lib/server-fetch'

export const dynamic = 'force-dynamic'

/**
 * Dynamic segment: the parameterized route (with and without generateStaticParams)
 * shows up in traces under its parameterized name (GET /dynamic/[id]).
 */
export default async function DynamicPage({
	params,
}: {
	params: Promise<{ id: string }>
}) {
	const { id } = await params
	console.info('[dynamic] rendering /dynamic/[id]', { id })

	LDObserve.recordCount({
		name: 'demo.dynamic.renders',
		value: 1,
		tags: [{ name: 'id', value: id }],
	})

	// Nested call showing the session header forwarded into the trace waterfall.
	const echoed = await serverFetch('/api/echo', {
		method: 'POST',
		body: JSON.stringify({ dynamicId: id }),
		headers: { 'content-type': 'application/json' },
	})
	await echoed.json().catch(() => null)

	return (
		<div style={{ display: 'grid', gap: 8 }}>
			<h1 style={{ margin: 0 }}>Dynamic route</h1>
			<p>
				id = <code>{id}</code>
			</p>
		</div>
	)
}
