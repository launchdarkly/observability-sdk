'use client'

// Minimal client fallback UI for the root ErrorBoundary (needs onClick, so it
// must be a client component; the boundary itself lives in the server layout).
export function RootErrorFallback() {
	return (
		<div style={{ padding: 24 }}>
			<h1>Uncaught error in the app tree</h1>
			<p>
				This error was reported to LaunchDarkly via the root
				&lt;ErrorBoundary&gt; (plus the global window error listeners of
				the observability SDK).
			</p>
			<button onClick={() => window.location.reload()}>Reload</button>
		</div>
	)
}
