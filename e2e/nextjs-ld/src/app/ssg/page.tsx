import { LDObserve } from '@launchdarkly/observability-next/server'

/**
 * Static page: rendered once at `next build` time. The build-time render runs
 * in the Next build process, so any console/error output there is captured as
 * long as observability registers during build (instrumentation.ts runs for
 * the build worker too).
 */
export default function SsgPage() {
	// LDObserve calls are safe when observability has not been initialized —
	// they no-op with a warning. During `next start` / revalidation this span
	// also appears on statically-rerendered paths.
	LDObserve.recordCount({
		name: 'demo.ssg.renders',
		value: 1,
		tags: [{ name: 'page', value: '/ssg' }],
	})

	return (
		<div style={{ display: 'grid', gap: 8 }}>
			<h1 style={{ margin: 0 }}>SSG page</h1>
			<p>This page was rendered once at build time.</p>
			<p>
				Check the LaunchDarkly <em>Traces</em> view for a{' '}
				<code>GET /ssg</code> span emitted by the{' '}
				<code>next build</code> prerender and by any server-side (RSC
				payload) render of the route.
			</p>
		</div>
	)
}
