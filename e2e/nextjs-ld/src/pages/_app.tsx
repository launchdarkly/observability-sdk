import type { AppProps } from 'next/app'

import {
	ErrorBoundary,
	LDObservabilityInit,
} from '@launchdarkly/observability-next/client'

import { CONSTANTS } from '@/constants'

export default function App({ Component, pageProps }: AppProps) {
	return (
		<ErrorBoundary>
			<LDObservabilityInit
				sdkKey={CONSTANTS.LAUNCHDARKLY_CLIENT_SIDE_ID}
				serviceName={CONSTANTS.FRONTEND_SERVICE_NAME}
				environment={CONSTANTS.OBSERVE_ENVIRONMENT}
				tracingOrigins
				networkRecording={{ enabled: true, recordHeadersAndBody: true }}
			/>
			<div style={{ padding: 24, fontFamily: 'system-ui, sans-serif' }}>
				<nav style={{ marginBottom: 16 }}>
					<a href="/">← back to App Router home</a>
				</nav>
				<Component {...pageProps} />
			</div>
		</ErrorBoundary>
	)
}
