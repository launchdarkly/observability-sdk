import { LDObserve } from '@launchdarkly/observability'
import { LDRecord } from '@launchdarkly/session-replay'
import { useEffect, useRef, useState } from 'react'
// import { client } from '../ldclient'
import { client, recordSession, recordObservability } from '../ldclientLazy'

export default function Root() {
	const fillColor = 'lightblue'
	const canvasRef = useRef<HTMLCanvasElement>(null)
	const [flags, setFlags] = useState<string>()
	const [session, setSession] = useState<string>()
	const [sessionKey, setSessionKey] = useState<string>('task129')

	useEffect(() => {
		const canvas = canvasRef.current
		if (!canvas) return
		const ctx = canvas.getContext('2d')!
		// Fill the entire canvas with the specified color
		ctx.fillStyle = fillColor
		ctx.fillRect(0, 0, canvas.width, canvas.height)
	}, [fillColor])

	useEffect(() => {
		const int = setInterval(() => {
			const url = LDRecord.getSession()?.url
			setSession(url)
			console.log('session url', url)
			LDObserve.recordLog('session url LDObserve', 'info', { url })
			LDObserve.recordLog(
				{ message: 'session url LDObserve', url },
				'info',
			)
		}, 1000)
		return () => {
			clearInterval(int)
		}
	}, [])

	return (
		<div id="sidebar">
			<h1>Hello, world</h1>
			<nav style={{ display: 'flex', gap: 12 }}>
				<a href="/welcome">Welcome</a>
				<a href="/privacy">Privacy Demo</a>
			</nav>
			<p>{flags}</p>
			<a href={session} target={'_blank'}>
				{session}
			</a>
			<canvas width="100" height="100" ref={canvasRef}></canvas>
			<button
				onClick={() => {
					LDObserve.recordLog('hello', 'info')
				}}
			>
				LDObserve.recordLog
			</button>
			<button
				onClick={() => {
					console.error('oh', 'no', {
						my: 'object',
						err: new Error(),
					})
				}}
			>
				console.error
			</button>
			<button
				onClick={() => {
					LDObserve.recordError(new Error('test error'))
				}}
			>
				LDObserve.consumeError
			</button>
			<button
				onClick={() => {
					if (canvasRef.current) {
						LDRecord.snapshot(canvasRef.current)
					}
				}}
			>
				LDRecord.snapshot
			</button>
			<div>
				<input
					type="text"
					value={sessionKey}
					onChange={(e) => setSessionKey(e.target.value)}
					placeholder="Enter session key"
					style={{ marginRight: '10px' }}
				/>
				<button
					onClick={() => {
						LDRecord.start({ forceNew: true, sessionKey })
					}}
				>
					LDRecord.start(forceNewWithSessionKey)
				</button>
			</div>
			<button
				onClick={() => {
					LDRecord.start({ forceNew: true })
				}}
			>
				LDRecord.start(forceNew)
			</button>
			<button
				onClick={() => {
					throw new Error('thrown error')
				}}
			>
				throw error
			</button>
			<button
				onClick={() => {
					LDObserve.recordGauge({
						name: 'my-random-metric',
						value: Math.floor(Math.random() * 100),
					})
				}}
			>
				LDObserve.recordGauge(random value)
			</button>
			<button
				onClick={() => {
					LDObserve.recordGauge({
						name: 'my-metric',
						value: 0,
					})
				}}
			>
				LDObserve.recordGauge(0 value)
			</button>
			<button
				onClick={() => {
					LDObserve.recordGauge({
						name: 'my-metric-attrs',
						value: 0,
						attributes: { foo: 'bar', baz: 42 },
					})
				}}
			>
				LDObserve.recordGauge(0 value w attrs)
			</button>
			<button
				onClick={async () => {
					setFlags(
						JSON.stringify(
							client.variation('enable-session-card-style'),
						),
					)
				}}
			>
				client.eval
			</button>
			<button
				onClick={async () => {
					await client.identify({
						kind: 'user',
						key: 'vadim@highlight.io',
					})
					setFlags(JSON.stringify(client.allFlags()))
				}}
			>
				client.identify
			</button>
			<button
				onClick={async () => {
					await client.identify({
						kind: 'multi',
						account: {
							hasExperimentationMAU: false,
							hasADSEvents: true,
							enableAccountImpersonation: false,
							planType: 'Enterprise',
							isCanceled: false,
							isTrial: false,
							organization: 'End-to-End Test Account',
							isSelfServe: false,
							hasHIPAAEnabled: false,
							hasActiveEnterpriseCampaign: false,
							hasExperimentationEvents: true,
							hasExperimentationKeys: false,
							isUsingExperimentation2022: false,
							hasSSO: true,
							signupDate: 1593470120619,
							isBeta: false,
							isLapsed: false,
							hasConfiguredSSO: false,
							owner: {
								email: 'e2e@launchdarkly.com',
							},
							planVersion: 1,
							postV2Signup: true,
							name: 'End-to-End Test Account',
							key: '5efa6ca891e30321f08aac4b',
						},
						environment: {
							name: 'Test',
							key: '65006c1cfd354512d19019d8',
						},
						member: {
							hasAdminRights: true,
							isEmailVerified: true,
							email: 'vkorolik@launchdarkly.com',
							createdDate: 1744385936713,
							featurePreviews: [
								'simplified-toggle-ux',
								'improved-context-targeting-experience',
								'new-experience',
							],
							name: 'Vadim Korolik',
							key: '67f93790b1bc7808f4b033be',
						},
						project: {
							key: '65006c1cfd354512d19019da',
						},
						user: {
							environmentId: '65006c1cfd354512d19019d8',
							hasExperimentationEvents: true,
							hasHIPAAEnabled: false,
							hasAdminRights: true,
							projectId: '65006c1cfd354512d19019da',
							isSelfServe: false,
							dogfoodCanary: false,
							isBeta: false,
							enableAccountImpersonation: false,
							hasExperimentationKeys: false,
							hasSSO: true,
							planVersion: 1,
							isUsingExperimentation2022: false,
							memberEmail: 'vkorolik@launchdarkly.com',
							isCanceled: false,
							memberVerifiedEmail: true,
							memberId: '67f93790b1bc7808f4b033be',
							accountId: '5efa6ca891e30321f08aac4b',
							organization: 'End-to-End Test Account',
							hasActiveEnterpriseCampaign: false,
							enableAccountSupportGenAi: false,
							hasADSEvents: true,
							email: 'e2e@launchdarkly.com',
							planType: 'Enterprise',
							postV2Signup: true,
							hasConfiguredSSO: false,
							hasExperimentationMAU: false,
							isLapsed: false,
							signupDate: 1593470120619,
							isTrial: false,
							name: '',
							key: '5efa6ca891e30321f08aac4b',
						},
					})
					setFlags(JSON.stringify(client.allFlags()))
				}}
			>
				client.identify gonfalon
			</button>
			<button
				onClick={async () => {
					await client.identify({
						kind: 'multi',
						org: {
							key: 'my-org-key',
							someAttribute: 'my-attribute-value',
						},
						user: {
							key: 'my-user-key',
							firstName: 'Bob',
							lastName: 'Bobberson',
							_meta: {
								privateAttributes: ['firstName'],
							},
						},
					})
					setFlags(JSON.stringify(client.allFlags()))
				}}
			>
				client.identify multi
			</button>
			<button
				onClick={async () => {
					await recordSession()
				}}
			>
				recordSession
			</button>
			<button
				onClick={async () => {
					await recordObservability()
				}}
			>
				recordObservability
			</button>
			<button
				onClick={async () => {
					LDRecord.stop()
				}}
			>
				LDRecord.stop()
			</button>
			<button
				onClick={async () => {
					LDObserve.stop()
				}}
			>
				LDObserve.stop()
			</button>

			<div style={{ padding: '2rem' }}>
				<h3>HTTP Requests</h3>
				<div
					style={{
						display: 'flex',
						flexDirection: 'row',
						gap: '10px',
						flexWrap: 'wrap',
					}}
				>
					<button
						onClick={async () => {
							await fetch(
								'https://jsonplaceholder.typicode.com/posts/1',
							)
						}}
					>
						Trigger HTTP Request
					</button>
					<button
						onClick={async () => {
							await fetch('https://api.github.com/graphql', {
								method: 'POST',
								headers: {
									'Content-Type': 'application/json',
								},
								body: JSON.stringify({
									query: 'query { viewer { login } }',
								}),
							})
						}}
					>
						Trigger Anonymous GraphQL Request
					</button>
					<button
						onClick={async () => {
							await fetch('https://api.github.com/graphql', {
								method: 'POST',
								headers: {
									'Content-Type': 'application/json',
								},
								body: JSON.stringify({
									operationName: 'GetViewer',
									query: 'query GetViewer { viewer { login name } }',
								}),
							})
						}}
					>
						Trigger Named GraphQL Request
					</button>
					<button
						onClick={async () => {
							await fetch(
								'https://jsonplaceholder.typicode.com/posts',
								{
									method: 'POST',
									headers: {
										'Content-Type': 'application/json',
									},
									body: JSON.stringify({
										title: 'Test Post',
										body: 'This is a test post',
										userId: 1,
									}),
								},
							)
						}}
					>
						Trigger POST Request
					</button>
				</div>
			</div>

			<div
				style={{
					marginTop: 8,
					padding: 12,
					border: '1px solid #ddd',
					borderRadius: 6,
					maxWidth: 720,
				}}
			>
				<h3>Session Properties</h3>
				<p>
					Add custom session-level attributes via{' '}
					<code>LDRecord.addSessionProperties</code>.
				</p>
				<textarea
					style={{
						width: '100%',
						minHeight: 120,
						fontFamily: 'monospace',
					}}
					defaultValue='{"plan":"pro","favoriteColor":"seafoam"}'
					placeholder='{"key":"value"}'
				/>
				<div style={{ display: 'flex', gap: 8, marginTop: 8 }}>
					<button
						onClick={() => {
							const value =
								document.querySelector('textarea')?.value ?? ''

							LDRecord.addSessionProperties(JSON.parse(value))
						}}
					>
						Apply session properties
					</button>
				</div>
			</div>

			<div
				style={{
					marginTop: 16,
					padding: 16,
					border: '2px solid #ff6b6b',
					borderRadius: 8,
					maxWidth: 720,
					backgroundColor: '#fff5f5',
				}}
			>
				<h3 style={{ color: '#c92a2a', margin: '0 0 8px 0' }}>
					🐛 Customer Issue Tests (Session Replay Race Condition)
				</h3>
				<p
					style={{
						fontSize: '0.9em',
						color: '#666',
						marginBottom: 12,
					}}
				>
					Tests for customer issue: Session names inconsistent after
					identify() with manualStart:true.
					<br />
					<strong>Config:</strong> anonymous context,
					manualStart:true, contextFriendlyName returns user.key for
					multi-kind contexts.
				</p>
				<div
					style={{
						display: 'flex',
						flexDirection: 'column',
						gap: 8,
					}}
				>
					<button
						onClick={async () => {
							console.log('=== CUSTOMER SCENARIO TEST ===')
							console.log(
								'Step 1: SDK initialized with anonymous context and manualStart:true',
							)

							// Step 2: Simulate login - call identify with multi-kind context (user.key = email)
							console.log(
								'Step 2: Calling identify with multi-kind context (user.key = email)',
							)
							await client.identify({
								kind: 'multi',
								user: {
									key: 'customer-test@example.com',
									name: 'Test Customer',
								},
								organization: {
									key: 'org-12345',
									name: 'Acme Corp',
								},
								plan: {
									key: 'enterprise',
									isTrialing: false,
								},
							})

							// Step 3: Start recording AFTER identify (customer's flow)
							console.log(
								'Step 3: Starting recording AFTER identify',
							)
							await LDRecord.start()

							// Step 4: Add session properties
							console.log('Step 4: Adding session properties')
							LDRecord.addSessionProperties({
								accountId: 'org-12345',
								accountName: 'Acme Corp',
								planType: 'enterprise',
							})

							console.log('=== TEST COMPLETE ===')
							console.log(
								'Expected: Session should show "customer-test@example.com" as identifier',
							)
							alert(
								'Test complete! Check Session Replay list for "customer-test@example.com"',
							)
						}}
						style={{
							backgroundColor: '#ff6b6b',
							color: 'white',
							fontWeight: 'bold',
							padding: '12px 16px',
							border: 'none',
							borderRadius: 6,
							cursor: 'pointer',
						}}
					>
						🎯 Customer Scenario: Identify → Start → Add Properties
					</button>

					<button
						onClick={async () => {
							console.log('=== RACE CONDITION TEST ===')
							console.log(
								'Step 1: Starting recording (async, not awaited)',
							)
							const startPromise = LDRecord.start()

							console.log(
								'Step 2: Immediately calling identify (before start completes)',
							)
							await client.identify({
								kind: 'multi',
								user: {
									key: 'race-test@example.com',
									name: 'Race Test User',
								},
								organization: {
									key: 'org-race-test',
								},
							})

							console.log(
								'Step 3: Adding session properties (before start completes)',
							)
							LDRecord.addSessionProperties({
								testType: 'race-condition',
								timestamp: Date.now(),
							})

							console.log('Step 4: Waiting for start to complete')
							await startPromise

							console.log('=== TEST COMPLETE ===')
							console.log(
								'Expected: Session should show "race-test@example.com"',
							)
							alert(
								'Test complete! Check Session Replay list for "race-test@example.com"',
							)
						}}
						style={{
							backgroundColor: '#ffa502',
							color: 'white',
							fontWeight: 'bold',
							padding: '12px 16px',
							border: 'none',
							borderRadius: 6,
							cursor: 'pointer',
						}}
					>
						⚡ Race Condition: Start + Identify + Properties
						(simultaneous)
					</button>

					<button
						onClick={async () => {
							console.log('=== PROPERTIES BEFORE START TEST ===')

							// Add session properties BEFORE starting
							console.log(
								'Step 1: Adding session properties BEFORE start',
							)
							LDRecord.addSessionProperties({
								earlyProperty: 'set-before-start',
								timestamp: Date.now(),
							})

							// Now start
							console.log('Step 2: Starting recording')
							await LDRecord.start()

							// Add more properties after start
							console.log(
								'Step 3: Adding more properties AFTER start',
							)
							LDRecord.addSessionProperties({
								lateProperty: 'set-after-start',
							})

							console.log('=== TEST COMPLETE ===')
							console.log(
								'Expected: Both earlyProperty and lateProperty should appear in session',
							)
							alert(
								'Test complete! Check session properties for both early and late properties',
							)
						}}
						style={{
							backgroundColor: '#20c997',
							color: 'white',
							fontWeight: 'bold',
							padding: '12px 16px',
							border: 'none',
							borderRadius: 6,
							cursor: 'pointer',
						}}
					>
						📦 Properties Before Start Test
					</button>
				</div>
			</div>
		</div>
	)
}
