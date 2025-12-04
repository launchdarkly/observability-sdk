import { LDObserve } from '@launchdarkly/observability'
import { LDRecord } from '@launchdarkly/session-replay'
import { Component, useEffect, useRef, useState } from 'react'
// import { client } from '../ldclient'
import { client, recordSession, recordObservability } from '../ldclientLazy'

const images = {
	image1: <img src="data:image/jpeg;base64,iVBORw0KGgoAAAANSUhEUgAAABkAAAAZCAYAAADE6YVjAAAAGXRFWHRTb2Z0d2FyZQBBZG9iZSBJbWFnZVJlYWR5ccllPAAAAyJpVFh0WE1MOmNvbS5hZG9iZS54bXAAAAAAADw/eHBhY2tldCBiZWdpbj0i77u/IiBpZD0iVzVNME1wQ2VoaUh6cmVTek5UY3prYzlkIj8+IDx4OnhtcG1ldGEgeG1sbnM6eD0iYWRvYmU6bnM6bWV0YS8iIHg6eG1wdGs9IkFkb2JlIFhNUCBDb3JlIDUuMy1jMDExIDY2LjE0NTY2MSwgMjAxMi8wMi8wNi0xNDo1NjoyNyAgICAgICAgIj4gPHJkZjpSREYgeG1sbnM6cmRmPSJodHRwOi8vd3d3LnczLm9yZy8xOTk5LzAyLzIyLXJkZi1zeW50YXgtbnMjIj4gPHJkZjpEZXNjcmlwdGlvbiByZGY6YWJvdXQ9IiIgeG1sbnM6eG1wPSJodHRwOi8vbnMuYWRvYmUuY29tL3hhcC8xLjAvIiB4bWxuczp4bXBNTT0iaHR0cDovL25zLmFkb2JlLmNvbS94YXAvMS4wL21tLyIgeG1sbnM6c3RSZWY9Imh0dHA6Ly9ucy5hZG9iZS5jb20veGFwLzEuMC9zVHlwZS9SZXNvdXJjZVJlZiMiIHhtcDpDcmVhdG9yVG9vbD0iQWRvYmUgUGhvdG9zaG9wIENTNiAoV2luZG93cykiIHhtcE1NOkluc3RhbmNlSUQ9InhtcC5paWQ6MEVBMTczNDg3QzA5MTFFNjk3ODM5NjQyRjE2RjA3QTkiIHhtcE1NOkRvY3VtZW50SUQ9InhtcC5kaWQ6MEVBMTczNDk3QzA5MTFFNjk3ODM5NjQyRjE2RjA3QTkiPiA8eG1wTU06RGVyaXZlZEZyb20gc3RSZWY6aW5zdGFuY2VJRD0ieG1wLmlpZDowRUExNzM0NjdDMDkxMUU2OTc4Mzk2NDJGMTZGMDdBOSIgc3RSZWY6ZG9jdW1lbnRJRD0ieG1wLmRpZDowRUExNzM0NzdDMDkxMUU2OTc4Mzk2NDJGMTZGMDdBOSIvPiA8L3JkZjpEZXNjcmlwdGlvbj4gPC9yZGY6UkRGPiA8L3g6eG1wbWV0YT4gPD94cGFja2V0IGVuZD0iciI/PjjUmssAAAGASURBVHjatJaxTsMwEIbpIzDA6FaMMPYJkDKzVYU+QFeEGPIKfYU8AETkCYI6wANkZQwIKRNDB1hA0Jrf0rk6WXZ8BvWkb4kv99vn89kDrfVexBSYgVNwDA7AN+jAK3gEd+AlGMGIBFDgFvzouK3JV/lihQTOwLtOtw9wIRG5pJn91Tbgqk9kSk7GViADrTD4HCyZ0NQnomi51sb0fUyCMQEbp2WpU67IjfNjwcYyoUDhjJVcZBjYBy40j4wXgaobWoe8Z6Y80CJBwFpunepIzt2AUgFjtXXshNXjVmMh+K+zzp/CMs0CqeuzrxSRpbOKfdCkiMTS1VBQ41uxMyQR2qbrXiiwYN3ACh1FDmsdK2Eu4J6Tlo31dYVtCY88h5ELZIJJ+IRMzBHfyJINrigNkt5VsRiub9nXICdsYyVd2NcVvA3ScE5t2rb5JuEeyZnAhmLt9NK63vX1O5Pe8XaPSuGq1uTrfUgMEp9EJ+CQvr+BJ/AAKvAcCiAR+bf9CjAAluzmdX4AEIIAAAAASUVORK5CYII=" />,
	image2: <img src="data:image/png;base64,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" />,
	image3: <img src="data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAABgAAAAYCAYAAADgdz34AAAABHNCSVQICAgIfAhkiAAAAAlwSFlzAAAApgAAAKYB3X3/OAAAABl0RVh0U29mdHdhcmUAd3d3Lmlua3NjYXBlLm9yZ5vuPBoAAANCSURBVEiJtZZPbBtFFMZ/M7ubXdtdb1xSFyeilBapySVU8h8OoFaooFSqiihIVIpQBKci6KEg9Q6H9kovIHoCIVQJJCKE1ENFjnAgcaSGC6rEnxBwA04Tx43t2FnvDAfjkNibxgHxnWb2e/u992bee7tCa00YFsffekFY+nUzFtjW0LrvjRXrCDIAaPLlW0nHL0SsZtVoaF98mLrx3pdhOqLtYPHChahZcYYO7KvPFxvRl5XPp1sN3adWiD1ZAqD6XYK1b/dvE5IWryTt2udLFedwc1+9kLp+vbbpoDh+6TklxBeAi9TL0taeWpdmZzQDry0AcO+jQ12RyohqqoYoo8RDwJrU+qXkjWtfi8Xxt58BdQuwQs9qC/afLwCw8tnQbqYAPsgxE1S6F3EAIXux2oQFKm0ihMsOF71dHYx+f3NND68ghCu1YIoePPQN1pGRABkJ6Bus96CutRZMydTl+TvuiRW1m3n0eDl0vRPcEysqdXn+jsQPsrHMquGeXEaY4Yk4wxWcY5V/9scqOMOVUFthatyTy8QyqwZ+kDURKoMWxNKr2EeqVKcTNOajqKoBgOE28U4tdQl5p5bwCw7BWquaZSzAPlwjlithJtp3pTImSqQRrb2Z8PHGigD4RZuNX6JYj6wj7O4TFLbCO/Mn/m8R+h6rYSUb3ekokRY6f/YukArN979jcW+V/S8g0eT/N3VN3kTqWbQ428m9/8k0P/1aIhF36PccEl6EhOcAUCrXKZXXWS3XKd2vc/TRBG9O5ELC17MmWubD2nKhUKZa26Ba2+D3P+4/MNCFwg59oWVeYhkzgN/JDR8deKBoD7Y+ljEjGZ0sosXVTvbc6RHirr2reNy1OXd6pJsQ+gqjk8VWFYmHrwBzW/n+uMPFiRwHB2I7ih8ciHFxIkd/3Omk5tCDV1t+2nNu5sxxpDFNx+huNhVT3/zMDz8usXC3ddaHBj1GHj/As08fwTS7Kt1HBTmyN29vdwAw+/wbwLVOJ3uAD1wi/dUH7Qei66PfyuRj4Ik9is+hglfbkbfR3cnZm7chlUWLdwmprtCohX4HUtlOcQjLYCu+fzGJH2QRKvP3UNz8bWk1qMxjGTOMThZ3kvgLI5AzFfo379UAAAAASUVORK5CYII=" />
} as const

export default function Root() {
	const fillColor = 'lightblue'
	const canvasRef = useRef<HTMLCanvasElement>(null)
	const [flags, setFlags] = useState<string>()
	const [session, setSession] = useState<string>()
	const [sessionKey, setSessionKey] = useState<string>('task129')
	const [image, setImage] = useState<keyof typeof images>("image1")

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

	const Image = images[image]

	return (
		<div id="sidebar">
			<h1>Replays</h1>
			<nav style={{ display: 'flex', gap: 12 }}>
				<a href="/welcome">Welcome</a>
				<a href="/privacy">Privacy Demo</a>
			</nav>
			<p>{flags}</p>
			<a href={session} target={'_blank'}>
				{session}
			</a>
			<div id = "imgcontainer">
			{Image}
			</div>
			<button
				onClick={() => {
					    setImage("image2")
				}}
			>
				LDObserve.recordLog
			</button>


			<button
				onClick={() => {
					setImage("image3")
				}}
			>
				LDObserve.recordLog
			</button>

		<button
				onClick={() => {
					const img = document.querySelector('img')
					if (img) {
						const newImg = document.createElement('img')
						newImg.width = (img as HTMLImageElement).width
						newImg.src = "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAUAAAAFCAYAAACNbyblAAAAHElEQVQI12P4//8/w38GIAXDIBKE0DHxgljNBAAO9TXL0Y4OHwAAAABJRU5ErkJggg=="
						//newImg.src = "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAUAAAAFCAYAAACNbyblAAAAHElEQVQI12P4//8/w38GIAXDIBKE0DHxgljNBAAO9TXL0Y4OHwAAAABJRU5ErkJggg=="
						img.replaceWith(newImg)
						return
					}
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
		</div>
	)
}
