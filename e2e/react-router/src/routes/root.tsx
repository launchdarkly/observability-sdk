// import { initialize as init } from '@launchdarkly/js-client-sdk'
import { initialize as init } from 'launchdarkly-js-client-sdk'
import Observability, { LDObserve } from '@launchdarkly/observability'
import SessionReplay, { LDRecord } from '@launchdarkly/session-replay'
import { useEffect, useRef, useState } from 'react'
// import { LD } from '@launchdarkly/browser'

export const client = init(
	'66d9d3c255856f0fa8fd62d0',
	{ key: 'unknown' },
	{
		// Not including plugins at all would be equivalent to the current LaunchDarkly SDK.
		plugins: [
			new Observability('1', {
				networkRecording: {
					enabled: true,
					recordHeadersAndBody: true,
				},
				serviceName: 'ryan-test',
				backendUrl: 'https://pub.observability.ld-stg.launchdarkly.com',
				otel: {
					otlpEndpoint:
						'https://otel.observability.ld-stg.launchdarkly.com',
				},
			}),
			new SessionReplay('1', {
				debug: { clientInteractions: true, domRecording: true },
				privacySetting: 'none',
				serviceName: 'ryan-test',
				backendUrl: 'https://pub.observability.ld-stg.launchdarkly.com',
			}), // Could be omitted for customers who cannot use session replay.
		],
	},
)

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
			<p>{session}</p>
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
