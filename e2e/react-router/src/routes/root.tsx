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
