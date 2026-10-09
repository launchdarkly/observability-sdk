import { DemoButtons } from './demo-buttons'

export default function Home() {
	return (
		<div style={{ display: 'grid', gap: 16 }}>
			<section>
				<h1 style={{ margin: 0 }}>
					LaunchDarkly Observability + Next.js
				</h1>
				<p>
					This app is instrumented with{' '}
					<code>@launchdarkly/observability-next</code> running in
					standalone mode (no feature-flag client). Browse the buttons
					below and the nav links; then check the LaunchDarkly UI for
					the session replay, errors, logs, metrics and the backend
					OTel traces they generate.
				</p>
			</section>

			<DemoButtons />

			<section
				style={{
					background: '#eff6ff',
					padding: 16,
					borderRadius: 8,
					fontSize: 14,
				}}
			>
				<strong>What to verify in LaunchDarkly</strong>
				<ol style={{ margin: '8px 0 0', paddingLeft: 20 }}>
					<li>
						A <em>Session Replay</em> appears for your browser
						session. Open it and check that clicks, console messages
						and network calls are captured.
					</li>
					<li>
						Errors recorded above appear on the <em>Errors</em>{' '}
						page, linked to the session.
					</li>
					<li>
						Backend <em>Traces</em> exist for the API calls and page
						rendering (SSR/ISR/SSG/streaming), each carrying the
						secure session id forwarded by{' '}
						<code>middleware.ts</code>.
					</li>
					<li>
						Server logs (console.*) recorded in server components
						appear as logs linked to traces.
					</li>
				</ol>
			</section>
		</div>
	)
}
