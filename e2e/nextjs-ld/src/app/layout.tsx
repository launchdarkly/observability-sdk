import {
	ErrorBoundary,
	LDObservabilityInit,
} from '@launchdarkly/observability-next/client'
import Link from 'next/link'

import { CONSTANTS } from '@/constants'
import { RootErrorFallback } from './root-error-fallback'

// NOTE: keep this file a Server Component — the proxy flag is read from the
// server runtime (next.config env map) at render time and passed down as a
// serializable prop; client bundles cannot read next.config env values.

export const metadata = {
	title: 'LaunchDarkly Observability Next Demo',
	description:
		'End-to-end demo of @launchdarkly/observability-next: session replay on the frontend, OpenTelemetry traces/logs/errors on the backend.',
}

// The withLaunchDarklyConfig rewrites (next.config.mjs) set this env var to
// 'true' when the same-origin proxy rewrites are configured. When the proxy is
// disabled the browser SDK must talk directly to LaunchDarkly, so wire up the
// explicit (NEXT_PUBLIC_*) endpoints for that mode.
const proxyEnabled = process.env.configureLaunchDarklyProxy === 'true'

const directEndpoints = !proxyEnabled
	? ({
			...(process.env.NEXT_PUBLIC_LAUNCHDARKLY_BACKEND_URL
				? {
						backendUrl:
							process.env.NEXT_PUBLIC_LAUNCHDARKLY_BACKEND_URL,
					}
				: {}),
			...(process.env.NEXT_PUBLIC_LAUNCHDARKLY_OTLP_ENDPOINT
				? {
						otel: {
							otlpEndpoint:
								process.env
									.NEXT_PUBLIC_LAUNCHDARKLY_OTLP_ENDPOINT,
						},
					}
				: {}),
		} as const)
	: ({} as const)

export default function RootLayout({
	children,
}: {
	children: React.ReactNode
}) {
	return (
		<ErrorBoundary fallback={<RootErrorFallback />}>
			<LDObservabilityInit
				sdkKey={CONSTANTS.LAUNCHDARKLY_CLIENT_SIDE_ID}
				serviceName={CONSTANTS.FRONTEND_SERVICE_NAME}
				environment={CONSTANTS.OBSERVE_ENVIRONMENT}
				// tracingOrigins=true attaches the x-highlight-request session header
				// to same-origin fetches, linking browser sessions to backend traces.
				tracingOrigins
				networkRecording={{ enabled: true, recordHeadersAndBody: true }}
				application={{
					id: 'nextjs-ld-example',
					version: process.env.NEXT_PUBLIC_APP_VERSION ?? 'dev',
				}}
				useProxy={proxyEnabled}
				{...directEndpoints}
			/>
			<html lang="en">
				<body
					style={{
						fontFamily: 'system-ui, sans-serif',
						margin: 0,
						background: '#f8fafc',
						color: '#0f172a',
					}}
				>
					<nav
						style={{
							display: 'flex',
							gap: 16,
							padding: '12px 24px',
							borderBottom: '1px solid #e2e8f0',
							flexWrap: 'wrap',
							alignItems: 'center',
						}}
					>
						<Link href="/" style={{ fontWeight: 700 }}>
							nextjs-ld
						</Link>
						<Link href="/ssr">SSR</Link>
						<Link href="/isr">ISR</Link>
						<Link href="/ssg">SSG</Link>
						<Link href="/streaming">Streaming</Link>
						<Link href="/server-action">Server Action</Link>
						<Link href="/client-nav">Client Nav</Link>
						<Link href="/dynamic/42">Dynamic</Link>
						<Link href="/legacy">Pages Router</Link>
					</nav>
					<main style={{ padding: 24, maxWidth: 900 }}>
						{children}
					</main>
				</body>
			</html>
		</ErrorBoundary>
	)
}
