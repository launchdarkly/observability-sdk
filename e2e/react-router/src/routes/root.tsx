import { LDObserve } from '@launchdarkly/observability'
import { LDRecord } from '@launchdarkly/session-replay'
import { useEffect, useRef, useState } from 'react'
import { client } from '../ldclient'

export default function Root() {
	const fillColor = 'lightblue'
	const canvasRef = useRef<HTMLCanvasElement>(null)
	const [flags, setFlags] = useState<string>()
	const [session, setSession] = useState<string>()

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
		</div>
	)
}
