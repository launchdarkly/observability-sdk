import { GetServerSidePropsContext } from 'next'

import { LDObserve } from '@launchdarkly/observability-next/client'

import { SERVER_ORIGIN } from '@/constants'

export default function LegacyHome({
	renderedAt,
	apiMessage,
}: {
	renderedAt: string
	apiMessage: string
}) {
	return (
		<div style={{ display: 'grid', gap: 12 }}>
			<h1 style={{ margin: 0 }}>Pages Router home (legacy)</h1>
			<p>
				SSR via <code>getServerSideProps</code> at{' '}
				<code>{renderedAt}</code> — every request produces a trace.
			</p>
			<p>
				Nested API result: <code>{apiMessage}</code>
			</p>
			<p>
				Session replay records this page too (the <code>_app</code>{' '}
				renders <code>LDObservabilityInit</code>).
			</p>
			<button
				onClick={() =>
					LDObserve.recordError(
						new Error('Pages Router client error'),
					)
				}
				style={{
					padding: '8px 12px',
					borderRadius: 6,
					justifySelf: 'start',
				}}
			>
				Record client error
			</button>
			<a href="/legacy/oops">Throw an SSR error (GET /legacy/oops)</a>
			<a href="/api/legacy-success">
				Call a wrapped Pages Router API — /api/legacy-success
			</a>
			<a href="/api/legacy-error">
				Call a wrapped Pages Router API — /api/legacy-error
			</a>
		</div>
	)
}

export async function getServerSideProps({ req }: GetServerSidePropsContext) {
	console.info('[pages] rendering /legacy via getServerSideProps')

	// Cookies and the x-highlight-request session header are NOT automatically
	// forwarded on server-side fetches — forward them explicitly so the nested
	// API trace stays linked to the same browser session.
	const api = await fetch(`${SERVER_ORIGIN}/api/test?success=true`, {
		headers: {
			...(req.headers.cookie ? { cookie: req.headers.cookie } : {}),
			...(req.headers['x-highlight-request']
				? {
						'x-highlight-request': String(
							req.headers['x-highlight-request'],
						),
					}
				: {}),
		},
		cache: 'no-store',
	})
	const apiJson = (await api.json().catch(() => null)) as {
		message?: string
	} | null

	return {
		props: {
			renderedAt: new Date().toISOString(),
			apiMessage: apiJson?.message ?? `HTTP ${api.status}`,
		},
	}
}
