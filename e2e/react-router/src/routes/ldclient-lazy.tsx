import { LDObserve } from '@launchdarkly/observability'
import { LDRecord } from '@launchdarkly/session-replay'
import { useState } from 'react'
import { client, recordObservability, recordSession } from '../ldclientLazy'

export default function LDClientLazyPage() {
	const [flags, setFlags] = useState<string>()
	const [session, setSession] = useState<string>()
	const [started, setStarted] = useState(false)

	return (
		<div id="ldclient-lazy-page">
			<h1>LDClient Lazy (Manual Start)</h1>
			<p>
				Uses <code>ldclientLazy.tsx</code> — plugins have{' '}
				<code>manualStart: true</code> and must be started explicitly.
			</p>
			<nav style={{ marginBottom: 16 }}>
				<a href="/">← Home</a>
			</nav>

			<div style={{ display: 'flex', flexDirection: 'column', gap: 8, maxWidth: 400 }}>
				<button
					onClick={async () => {
						await recordObservability()
						await recordSession()
						setStarted(true)
					}}
				>
					Start plugins (recordObservability + recordSession)
				</button>
				{started && <p style={{ color: 'green' }}>Plugins started</p>}

				<button
					onClick={() => {
						const url = LDRecord.getSession()?.url
						setSession(url)
					}}
				>
					Get session URL
				</button>
				{session && (
					<a href={session} target="_blank" rel="noreferrer">
						{session}
					</a>
				)}

				<button
					onClick={() => {
						LDObserve.recordLog('hello from ldclient-lazy page', 'info')
					}}
				>
					LDObserve.recordLog
				</button>

				<button
					onClick={() => {
						LDObserve.recordError(new Error('test error from ldclient-lazy page'))
					}}
				>
					LDObserve.recordError
				</button>

				<button
					onClick={async () => {
						await client.identify({ kind: 'user', key: 'ldclient-lazy-test-user' })
						setFlags(JSON.stringify(client.allFlags(), null, 2))
					}}
				>
					client.identify
				</button>

				<button
					onClick={() => {
						setFlags(JSON.stringify(client.variation('enable-session-card-style'), null, 2))
					}}
				>
					client.variation
				</button>

				<button
					onClick={() => {
						client.track('ldclient-lazy-page-custom-event', { random: Math.random() })
					}}
				>
					client.track
				</button>

				{flags && (
					<pre style={{ background: '#f5f5f5', padding: 8, borderRadius: 4 }}>
						{flags}
					</pre>
				)}
			</div>
		</div>
	)
}
