import { LDObserve } from '@launchdarkly/observability-next/server'

export const revalidate = 15 // seconds

/**
 * ISR page: statically rendered, then re-validated every 15 seconds. In the
 * traces you should see "GET /isr" spans from the initial render and from each
 * background revalidation, with a growing time gap between them, and a
 * timestamp that only updates every revalidate window.
 */
export default async function IsrPage() {
	const renderedAt = new Date().toISOString()

	// ISR renders happen in the Node.js server process (background
	// revalidation included), so console logs and traces are captured too.
	console.info('[isr] rendering /isr', { renderedAt })

	LDObserve.recordCount({
		name: 'demo.isr.renders',
		value: 1,
		tags: [{ name: 'page', value: '/isr' }],
	})

	return (
		<div style={{ display: 'grid', gap: 8 }}>
			<h1 style={{ margin: 0 }}>ISR page</h1>
			<p>
				Rendered at <code>{renderedAt}</code>. This page revalidates
				every 15s: refresh within the window and the timestamp stays the
				same; after the window it changes (background regeneration).
			</p>
		</div>
	)
}
