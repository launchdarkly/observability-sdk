import { initialize } from '@launchdarkly/js-client-sdk'
import Observability, { LDObserve } from '@launchdarkly/observability'
import SessionReplay, { LDRecord } from '@launchdarkly/session-replay'
import { useEffect, useRef } from 'react'
// TODO(vkorolik)
// import { LD } from '@launchdarkly/browser'

const client = initialize('66d9d3c255856f0fa8fd62d0', {
	// Not including plugins at all would be equivalent to the current LaunchDarkly SDK.
	plugins: [
		new Observability('1', {
			networkRecording: {
				enabled: true,
				recordHeadersAndBody: true,
			},
			serviceName: 'ryan-test',
			backendUrl: 'https://pub.observability.ld-stg.launchdarkly.com',
			otlpEndpoint: 'https://otel.observability.ld-stg.launchdarkly.com',
		}),
		new SessionReplay('1', {
			serviceName: 'ryan-test',
			backendUrl: 'https://pub.observability.ld-stg.launchdarkly.com',
		}), // Could be omitted for customers who cannot use session replay.
	],
})

export default function Root() {
	const fillColor = 'lightblue'
	const canvasRef = useRef<HTMLCanvasElement>(null)

	useEffect(() => {
		const canvas = canvasRef.current
		if (!canvas) return
		const ctx = canvas.getContext('2d')!
		// Fill the entire canvas with the specified color
		ctx.fillStyle = fillColor
		ctx.fillRect(0, 0, canvas.width, canvas.height)
	}, [fillColor])

	return (
		<div id="sidebar">
			<h1>Hello, world</h1>
			<p>{JSON.stringify(client.allFlags())}</p>
			<canvas width="100" height="100" ref={canvasRef}></canvas>
			<button
				onClick={() => {
					LDObserve.recordLog('hello', 'INFO')
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
					client.identify({
						kind: 'user',
						key: 'vadim@highlight.io',
					})
				}}
			>
				client.identify
			</button>
		</div>
	)
}
