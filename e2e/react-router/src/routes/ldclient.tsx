import { LDObserve } from '@launchdarkly/observability'
import { LDRecord } from '@launchdarkly/session-replay'
import { useState } from 'react'
import { Link, Outlet, useLocation } from 'react-router-dom'
import { client } from '../ldclient'

const subPages = [
	{ path: '/ldclient', label: 'Home' },
	{ path: '/ldclient/page-a', label: 'Page A' },
	{ path: '/ldclient/page-b', label: 'Page B' },
	{ path: '/ldclient/page-c', label: 'Page C' },
]

export default function LDClientPage() {
	const [flags, setFlags] = useState<string>()
	const [session, setSession] = useState<string>()
	const location = useLocation()

	return (
		<div id="ldclient-page">
			<h1>LDClient (Auto-Start)</h1>
			<p>
				Uses <code>ldclient.tsx</code> — plugins start automatically.
				Navigate between sub-pages to test page view tracking while
				recording.
			</p>
			<nav style={{ display: 'flex', gap: 12, marginBottom: 16 }}>
				<a href="/">← Home</a>
				{subPages.map(({ path, label }) => (
					<Link
						key={path}
						to={path}
						style={{
							fontWeight:
								location.pathname === path ? 'bold' : 'normal',
							textDecoration:
								location.pathname === path
									? 'underline'
									: 'none',
						}}
					>
						{label}
					</Link>
				))}
			</nav>

			<div
				style={{
					display: 'flex',
					flexDirection: 'column',
					gap: 8,
					maxWidth: 400,
				}}
			>
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
						LDObserve.recordLog('hello from ldclient page', 'info')
					}}
				>
					LDObserve.recordLog
				</button>

				<button
					onClick={() => {
						LDObserve.recordError(
							new Error('test error from ldclient page'),
						)
					}}
				>
					LDObserve.recordError
				</button>

				<button
					onClick={async () => {
						await client.identify({
							kind: 'user',
							key: 'ldclient-test-user',
						})
						setFlags(JSON.stringify(client.allFlags(), null, 2))
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
								owner: { email: 'e2e@launchdarkly.com' },
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
							project: { key: '65006c1cfd354512d19019da' },
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
						setFlags(JSON.stringify(client.allFlags(), null, 2))
					}}
				>
					client.identify gonfalon
				</button>

				<button
					onClick={() => {
						setFlags(
							JSON.stringify(
								client.variation('enable-session-card-style'),
								null,
								2,
							),
						)
					}}
				>
					client.variation
				</button>

				<button
					onClick={() => {
						client.track('ldclient-page-custom-event', {
							random: Math.random(),
						})
					}}
				>
					client.track
				</button>

				{flags && (
					<pre
						style={{
							background: '#f5f5f5',
							padding: 8,
							borderRadius: 4,
						}}
					>
						{flags}
					</pre>
				)}
			</div>

			<div style={{ marginTop: 24 }}>
				<Outlet />
			</div>
		</div>
	)
}

export function LDClientPageA() {
	return (
		<div
			id="ldclient-page-a"
			style={{
				padding: 16,
				background: '#e8f4fd',
				borderRadius: 8,
				maxWidth: 500,
			}}
		>
			<h2>Page A</h2>
			<p>
				This page has a <strong>form</strong> to test input capture
				during recording.
			</p>
			<form onSubmit={(e) => e.preventDefault()}>
				<div
					style={{ display: 'flex', flexDirection: 'column', gap: 8 }}
				>
					<label>
						Name
						<input
							type="text"
							placeholder="Enter your name"
							style={{
								display: 'block',
								marginTop: 4,
								width: '100%',
							}}
						/>
					</label>
					<label>
						Email
						<input
							type="email"
							placeholder="Enter your email"
							style={{
								display: 'block',
								marginTop: 4,
								width: '100%',
							}}
						/>
					</label>
					<button type="submit">Submit</button>
				</div>
			</form>
		</div>
	)
}

export function LDClientPageB() {
	const [count, setCount] = useState(0)
	return (
		<div
			id="ldclient-page-b"
			style={{
				padding: 16,
				background: '#e8fde8',
				borderRadius: 8,
				maxWidth: 500,
			}}
		>
			<h2>Page B</h2>
			<p>
				This page has a <strong>counter</strong> to test DOM mutation
				capture during recording.
			</p>
			<div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
				<button onClick={() => setCount((c) => c - 1)}>−</button>
				<span
					style={{
						fontSize: 24,
						fontWeight: 'bold',
						minWidth: 40,
						textAlign: 'center',
					}}
				>
					{count}
				</span>
				<button onClick={() => setCount((c) => c + 1)}>+</button>
			</div>
		</div>
	)
}

export function LDClientPageC() {
	const [visible, setVisible] = useState(true)
	return (
		<div
			id="ldclient-page-c"
			style={{
				padding: 16,
				background: '#fdf5e8',
				borderRadius: 8,
				maxWidth: 500,
			}}
		>
			<h2>Page C</h2>
			<p>
				This page has a <strong>toggling section</strong> to test node
				add/remove during recording.
			</p>
			<button onClick={() => setVisible((v) => !v)}>
				{visible ? 'Hide' : 'Show'} content
			</button>
			{visible && (
				<div
					style={{
						marginTop: 12,
						padding: 12,
						background: '#fff3cd',
						borderRadius: 4,
					}}
				>
					<p>This content can be toggled on and off.</p>
					<ul>
						<li>Item one</li>
						<li>Item two</li>
						<li>Item three</li>
					</ul>
				</div>
			)}
		</div>
	)
}
