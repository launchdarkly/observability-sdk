'use client'

import { LDObserve } from '@launchdarkly/observability-next/client'

export default function Home() {
	return (
		<main style={{ padding: 24, fontFamily: 'sans-serif' }}>
			<h1>LaunchDarkly Observability + Next.js</h1>
			<p>
				This app is instrumented with{' '}
				<code>@launchdarkly/observability-next</code> running in
				standalone mode (no feature-flag client).
			</p>
			<button
				onClick={() => LDObserve.recordError(new Error('manual error'))}
			>
				Record an error
			</button>{' '}
			<button onClick={() => fetch('/api/test?success=true')}>
				Call traced API
			</button>
		</main>
	)
}
