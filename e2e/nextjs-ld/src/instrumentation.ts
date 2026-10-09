// Next.js instrumentation hook — runs once per server process.
// This is the canonical place to initialize server-side LaunchDarkly
// observability so that SSR (app router), getServerSideProps (pages router),
// middleware and route handlers all produce OpenTelemetry spans/exporter data.
export async function register() {
	// The OTel-based node SDK is Node-only; guard the runtime explicitly.
	if (process.env.NEXT_RUNTIME !== 'nodejs') return

	const { registerObservability } =
		await import('@launchdarkly/observability-next/server')
	// Servers must follow the same endpoint selection as the browser proxy:
	// resolveLDEndpoints honors explicit options > LAUNCHDARKLY_OTEL_ENDPOINT /
	// LAUNCHDARKLY_BACKEND_URL env vars > LAUNCHDARKLY_ENV=staging hosts > prod.
	const { resolveLDEndpoints } =
		await import('@launchdarkly/observability-next/config')
	const { CONSTANTS } = await import('@/constants')
	const endpoints = resolveLDEndpoints()

	await registerObservability({
		sdkKey: CONSTANTS.LAUNCHDARKLY_SDK_KEY,
		serviceName: CONSTANTS.BACKEND_SERVICE_NAME,
		environment: CONSTANTS.OBSERVE_ENVIRONMENT,
		otlpEndpoint: endpoints.otlpEndpoint,
		backendUrl: endpoints.backendUrl,
	})
}
