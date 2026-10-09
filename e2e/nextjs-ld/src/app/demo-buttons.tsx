'use client'

import {
	ErrorBoundary,
	LDObserve,
} from '@launchdarkly/observability-next/client'
import { useState } from 'react'

async function call(path: string, method = 'GET', body?: unknown) {
	const res = await fetch(path, {
		method,
		headers: body ? { 'content-type': 'application/json' } : undefined,
		body: body ? JSON.stringify(body) : undefined,
	})
	return { status: res.status, json: await res.json().catch(() => null) }
}

function Button({
	label,
	onClick,
}: {
	label: string
	onClick: () => void | Promise<void>
}) {
	return (
		<button
			onClick={() => void onClick()}
			style={{
				padding: '8px 12px',
				borderRadius: 6,
				border: '1px solid #cbd5e1',
				background: 'white',
				cursor: 'pointer',
			}}
		>
			{label}
		</button>
	)
}

/**
 * A child that throws during render, scoped to its own <ErrorBoundary>, to
 * demonstrate boundary-reported errors without taking down the whole page.
 */
function CrashingChild({ crash }: { crash: boolean }) {
	if (crash) {
		throw new Error('Client render error (caught by <ErrorBoundary>)')
	}
	return <p>No crash yet — the first toggle throws during render.</p>
}

export function DemoButtons() {
	const [crashKey, setCrashKey] = useState(0)
	const [crashing, setCrashing] = useState(false)

	return (
		<div style={{ display: 'grid', gap: 12, justifyItems: 'start' }}>
			<fieldset
				style={{
					display: 'flex',
					gap: 8,
					flexWrap: 'wrap',
					border: '1px solid #cbd5e1',
					borderRadius: 8,
				}}
			>
				<legend>Errors</legend>
				<Button
					label="throw in event handler (uncaught)"
					onClick={() => {
						throw new Error('Client uncaught error (event handler)')
					}}
				/>
				<Button
					label="crash a component (ErrorBoundary)"
					onClick={() => {
						setCrashing((c) => !c)
						setCrashKey((k) => k + 1)
					}}
				/>
				<Button
					label="LDObserve.recordError"
					onClick={() =>
						LDObserve.recordError(
							new Error(
								'Recorded error via LDObserve.recordError',
							),
							'manual client error',
							{ 'demo.source': 'button' },
						)
					}
				/>
				<Button
					label="console.error"
					onClick={() =>
						console.error('Client console.error', { page: 'home' })
					}
				/>
				<Button
					label="console.info"
					onClick={() => console.info('Client console.info')}
				/>
			</fieldset>

			<fieldset
				style={{
					display: 'flex',
					gap: 8,
					flexWrap: 'wrap',
					border: '1px solid #cbd5e1',
					borderRadius: 8,
				}}
			>
				<legend>Traces / metrics / logs</legend>
				<Button
					label="startManualSpan"
					onClick={() => {
						LDObserve.startManualSpan(
							'client.manual-span',
							(span) => {
								span.setAttribute('demo.kind', 'manual')
								setTimeout(() => span.end(), 300)
							},
						)
					}}
				/>
				<Button
					label="recordCount + recordHistogram"
					onClick={() => {
						LDObserve.recordCount({
							name: 'demo.clicks',
							value: 1,
							attributes: { button: 'count' },
						})
						LDObserve.recordHistogram({
							name: 'demo.click.latency_ms',
							value: Math.round(Math.random() * 100),
							attributes: { button: 'histogram' },
						})
					}}
				/>
				<Button
					label="recordLog (custom)"
					onClick={() =>
						LDObserve.recordLog('Custom client log', 'info', {
							'demo.source': 'recordLog',
						})
					}
				/>
			</fieldset>

			<fieldset
				style={{
					display: 'flex',
					gap: 8,
					flexWrap: 'wrap',
					border: '1px solid #cbd5e1',
					borderRadius: 8,
				}}
			>
				<legend>
					API calls (backend traces, linked via x-highlight-request)
				</legend>
				<Button
					label="GET /api/test?success=true"
					onClick={() =>
						call('/api/test?success=true').then((r) =>
							console.log('api result', r),
						)
					}
				/>
				<Button
					label="GET /api/test (error)"
					onClick={() =>
						call('/api/test').then((r) =>
							console.log('api result', r),
						)
					}
				/>
				<Button
					label="POST /api/echo"
					onClick={() =>
						call('/api/echo', 'POST', {
							hello: 'world',
							n: 42,
						}).then((r) => console.log('echo result', r))
					}
				/>
				<Button
					label="GET /api/external"
					onClick={() =>
						call('/api/external').then((r) =>
							console.log('external result', r.status),
						)
					}
				/>
				<Button
					label="GET /api/slow?ms=1200"
					onClick={() =>
						call('/api/slow?ms=1200').then((r) =>
							console.log('slow result', r.status),
						)
					}
				/>
			</fieldset>

			<section>
				{/* The SDK <ErrorBoundary /> reports to LDObserve.recordError in
				    componentDidCatch. Remounting via key resets it. */}
				<ErrorBoundary
					fallback={(error) => (
						<div
							style={{
								padding: 12,
								background: '#fee2e2',
								borderRadius: 8,
							}}
						>
							<strong>ErrorBoundary caught:</strong>{' '}
							{error.message}
							<Button
								label="Reset"
								onClick={() => setCrashing(false)}
							/>
						</div>
					)}
				>
					<CrashingChild key={crashKey} crash={crashing} />
				</ErrorBoundary>
			</section>
		</div>
	)
}
