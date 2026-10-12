// Centralize env access so Next.js inlines the NEXT_PUBLIC_* values into the
// client bundle at build time.
//
// Endpoint selection summary:
// - Client telemetry is proxied through this app (same-origin) by the
//   withLaunchDarklyConfig rewrites in next.config.mjs. The proxy destinations
//   follow LAUNCHDARKLY_ENV ('staging' | 'production') or explicit
//   LAUNCHDARKLY_BACKEND_URL / LAUNCHDARKLY_OTEL_ENDPOINT env vars.
// - With the proxy disabled (configureLaunchDarklyProxy: false), the browser
//   SDK talks directly to the hosts in NEXT_PUBLIC_LAUNCHDARKLY_BACKEND_URL and
//   NEXT_PUBLIC_LAUNCHDARKLY_OTLP_ENDPOINT.
// - Server telemetry (@launchdarkly/observability-node) reads
//   LAUNCHDARKLY_OTEL_ENDPOINT / LAUNCHDARKLY_BACKEND_URL the same way.

export const CONSTANTS = {
	LAUNCHDARKLY_CLIENT_SIDE_ID:
		process.env.NEXT_PUBLIC_LAUNCHDARKLY_CLIENT_SIDE_ID ?? '',
	LAUNCHDARKLY_SDK_KEY: process.env.LAUNCHDARKLY_SDK_KEY ?? '',
	FRONTEND_SERVICE_NAME:
		process.env.NEXT_PUBLIC_LD_SERVICE_NAME ?? 'nextjs-ld-frontend',
	BACKEND_SERVICE_NAME: process.env.LD_SERVICE_NAME ?? 'nextjs-ld-backend',
	OBSERVE_ENVIRONMENT: process.env.LD_OBSERVE_ENVIRONMENT ?? 'e2e-test',
}

/**
 * Absolute origin the Next server can use to fetch its own API routes from
 * server components / SSR. Defaults to the local dev / start port.
 */
export const SERVER_ORIGIN =
	process.env.APP_ORIGIN ?? `http://127.0.0.1:${process.env.PORT ?? 3006}`
