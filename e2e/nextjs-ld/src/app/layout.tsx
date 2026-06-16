import {
	ErrorBoundary,
	LDObservabilityInit,
} from '@launchdarkly/observability-next/client'

import { CONSTANTS } from '@/constants'

export const metadata = {
	title: 'LaunchDarkly Observability Next Demo',
	description: 'Demonstrates @launchdarkly/observability-next',
}

export default function RootLayout({
	children,
}: {
	children: React.ReactNode
}) {
	return (
		<ErrorBoundary>
			<LDObservabilityInit
				sdkKey={CONSTANTS.LAUNCHDARKLY_CLIENT_SIDE_ID}
				serviceName="nextjs-ld-frontend"
				environment="e2e-test"
				tracingOrigins
				networkRecording={{ enabled: true, recordHeadersAndBody: true }}
			/>
			<html lang="en">
				<body>{children}</body>
			</html>
		</ErrorBoundary>
	)
}
