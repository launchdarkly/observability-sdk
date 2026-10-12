'use client'

import { useState, useTransition } from 'react'

import { createOrder, failOrder } from './actions'

type Result = { ok: boolean; bucket: string; message: string }

export default function ServerActionPage() {
	const [results, setResults] = useState<Result[]>([])
	const [pending, startTransition] = useTransition()

	const submit = (action: 'create' | 'fail') => {
		const data = new FormData()
		data.set('item', `demo-item-${Math.floor(Math.random() * 1000)}`)

		startTransition(async () => {
			try {
				const [fn, label] =
					action === 'create'
						? ([createOrder, 'createOrder'] as const)
						: ([failOrder, 'failOrder'] as const)
				const res = await fn(data)
				setResults((r) =>
					r.concat({
						ok: true,
						bucket: label,
						message: JSON.stringify(res ?? 'ok'),
					}),
				)
			} catch (e) {
				setResults((r) =>
					r.concat({
						ok: false,
						bucket: action,
						message: e instanceof Error ? e.message : String(e),
					}),
				)
			}
		})
	}

	return (
		<div style={{ display: 'grid', gap: 12 }}>
			<h1 style={{ margin: 0 }}>Server Actions</h1>
			<p>
				Server actions execute as POST spans under the page route. The
				failing action records an error visible in LaunchDarkly (linked
				to the active session via middleware).
			</p>
			<div>
				<button
					onClick={() => submit('create')}
					disabled={pending}
					style={{
						padding: '8px 12px',
						borderRadius: 6,
						marginRight: 8,
					}}
				>
					createOrder (success)
				</button>
				<button
					onClick={() => submit('fail')}
					disabled={pending}
					style={{ padding: '8px 12px', borderRadius: 6 }}
				>
					failOrder (error)
				</button>
			</div>
			{pending && <p>pending…</p>}
			{results.map((r, i) => (
				<div
					key={i}
					style={{
						padding: 8,
						borderRadius: 6,
						background: r.ok ? '#dcfce7' : '#fee2e2',
					}}
				>
					<strong>{r.bucket}</strong> — {r.message}
				</div>
			))}
		</div>
	)
}
