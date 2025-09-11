import { useState } from 'react'
import SplunkRum from '@splunk/otel-web'
import { initialize as initLD } from 'launchdarkly-js-client-sdk'
import Observability, { LDObserve } from '@launchdarkly/observability'

function OTelConflictTest() {
	const [initStatus, setInitStatus] = useState<string>('Not started')
	const [ldClient, setLdClient] = useState<ReturnType<typeof initLD> | null>(
		null,
	)
	const [logs, setLogs] = useState<string[]>([])

	const addLog = (message: string) => {
		console.log(message)
		setLogs((prev) => [
			...prev,
			`${new Date().toLocaleTimeString()}: ${message}`,
		])
	}

	const initializeStandaloneOTel = () => {
		try {
			addLog('🔧 Initializing Splunk RUM (OpenTelemetry)...')

			// Initialize Splunk RUM which sets up OpenTelemetry under the hood
			SplunkRum.init({
				beaconUrl: 'https://rum-ingest.us0.signalfx.com/v1/rum',
				rumAuth: 'test-token-for-conflict-demo',
				app: 'otel-conflict-test-app',
				version: '1.0.0',
				environment: 'test',
				// Enable debug mode to see what's happening
				debug: true,
			})

			addLog('✅ Splunk RUM (OpenTelemetry) initialized successfully!')

			// Test that it works by creating a custom span
			SplunkRum.provider
				.getTracer('test-tracer')
				.startSpan('test-span', {
					attributes: { 'test.attribute': 'splunk-rum' },
				})
				.end()

			addLog('✅ Created test span with Splunk RUM')
			return true
		} catch (error) {
			addLog(`❌ Error initializing Splunk RUM: ${error}`)
			return false
		}
	}

	const initializeLaunchDarklyWithOTel = async () => {
		try {
			addLog('🚀 Initializing LaunchDarkly with Observability plugin...')

			const observabilitySettings = {
				networkRecording: {
					enabled: true,
					recordHeadersAndBody: true,
				},
				serviceName: 'ld-conflict-test',
			}

			const client = initLD(
				'548f6741c1efad40031b18ae',
				{ key: 'otel-conflict-test-user' },
				{
					plugins: [new Observability(observabilitySettings)],
					baseUrl: 'https://ld-stg.launchdarkly.com',
					eventsUrl: 'https://events-stg.launchdarkly.com',
				},
			)

			await client.waitForInitialization()
			setLdClient(client)

			addLog(
				'✅ LaunchDarkly client with Observability plugin initialized!',
			)

			// Test that both OTel setups work
			LDObserve.recordLog(
				'Testing LaunchDarkly observability after OTel conflict',
				'info',
			)
			addLog('✅ Successfully recorded log via LDObserve')

			// Test the original Splunk RUM still works
			SplunkRum.provider
				.getTracer('test-tracer')
				.startSpan('post-ld-init-span', {
					attributes: { 'test.attribute': 'after-ld-init' },
				})
				.end()

			addLog('✅ Splunk RUM tracer still works after LD init')

			return true
		} catch (error) {
			addLog(`❌ Error initializing LaunchDarkly: ${error}`)
			return false
		}
	}

	const runFullTest = async () => {
		setInitStatus('Running...')
		setLogs([])

		// Step 1: Initialize standalone OTel first
		const otelSuccess = initializeStandaloneOTel()
		if (!otelSuccess) {
			setInitStatus('❌ Failed at standalone OTel step')
			return
		}

		// // Wait a bit to see the separation
		await new Promise((resolve) => setTimeout(resolve, 1000))

		// Step 2: Initialize LaunchDarkly with Observability plugin
		const ldSuccess = await initializeLaunchDarklyWithOTel()
		if (!ldSuccess) {
			setInitStatus('❌ Failed at LaunchDarkly step')
			return
		}

		setInitStatus('✅ All tests passed! No conflicts detected.')
	}

	return (
		<div style={{ padding: '20px', maxWidth: '800px' }}>
			<h1>OpenTelemetry Conflict Test (Splunk RUM)</h1>
			<p>
				This page tests that LaunchDarkly's Observability plugin can be
				initialized alongside Splunk RUM (which uses OpenTelemetry under
				the hood) without conflicts.
			</p>

			<div style={{ marginBottom: '20px' }}>
				<button
					onClick={runFullTest}
					style={{
						padding: '10px 20px',
						fontSize: '16px',
						backgroundColor: '#007bff',
						color: 'white',
						border: 'none',
						borderRadius: '4px',
						cursor: 'pointer',
					}}
				>
					Run Splunk RUM Conflict Test
				</button>
			</div>

			<div style={{ marginBottom: '20px' }}>
				<strong>Status:</strong> <span>{initStatus}</span>
			</div>

			{ldClient && (
				<div style={{ marginBottom: '20px' }}>
					<h3>Test Actions (available after successful init):</h3>
					<button
						onClick={() => {
							LDObserve.recordLog('Manual test log', 'info', {
								timestamp: Date.now(),
							})
							addLog('📝 Recorded manual test log')
						}}
						style={{ marginRight: '10px', padding: '5px 10px' }}
					>
						Record Test Log
					</button>
					<button
						onClick={() => {
							LDObserve.recordError(
								new Error('Test error for conflict testing'),
							)
							addLog('🚨 Recorded test error')
						}}
						style={{ marginRight: '10px', padding: '5px 10px' }}
					>
						Record Test Error
					</button>
					<button
						onClick={() => {
							SplunkRum.provider
								.getTracer('manual-test-tracer')
								.startSpan('manual-test-span', {
									attributes: {
										'manual.test': true,
										timestamp: Date.now(),
									},
								})
								.end()
							addLog(
								'🔍 Created manual test span with Splunk RUM',
							)
						}}
						style={{ padding: '5px 10px' }}
					>
						Create Splunk RUM Span
					</button>
				</div>
			)}

			<div>
				<h3>Logs:</h3>
				<div
					style={{
						backgroundColor: '#f8f9fa',
						border: '1px solid #dee2e6',
						borderRadius: '4px',
						padding: '10px',
						height: '400px',
						overflowY: 'auto',
						fontFamily: 'monospace',
						fontSize: '12px',
					}}
				>
					{logs.map((log, index) => (
						<div key={index} style={{ marginBottom: '2px' }}>
							{log}
						</div>
					))}
				</div>
			</div>

			<div style={{ marginTop: '20px', fontSize: '14px', color: '#666' }}>
				<p>
					<strong>What this test does:</strong>
				</p>
				<ol>
					<li>
						Initializes Splunk RUM (which sets up OpenTelemetry
						under the hood)
					</li>
					<li>
						Initializes LaunchDarkly client with Observability
						plugin
					</li>
					<li>Verifies both systems work without conflicts</li>
					<li>Tests that no "duplicate registration" errors occur</li>
				</ol>
				<p>
					<strong>Expected result:</strong> Both OTel systems should
					coexist peacefully, with the LaunchDarkly plugin detecting
					the existing OTel setup from Splunk RUM and working with it
					instead of causing conflicts.
				</p>
			</div>
		</div>
	)
}

export default OTelConflictTest
