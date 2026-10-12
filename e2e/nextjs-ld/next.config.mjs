// next.config.mjs
import { withLaunchDarklyConfig } from '@launchdarkly/observability-next/config'

/** @type {import('next').NextConfig} */
const nextConfig = {
	// The same-origin telemetry proxy (added by withLaunchDarklyConfig) forwards
	// /highlight-events and /v1/{traces,metrics,logs} to LaunchDarkly. Point it
	// at LaunchDarkly staging with env vars:
	//   LAUNCHDARKLY_ENV=staging
	// or explicit endpoints:
	//   LAUNCHDARKLY_BACKEND_URL=https://pub.observability.ld-stg.launchdarkly.com
	//   LAUNCHDARKLY_OTEL_ENDPOINT=https://otel.observability.ld-stg.launchdarkly.com:4318
}

export default withLaunchDarklyConfig(nextConfig)
