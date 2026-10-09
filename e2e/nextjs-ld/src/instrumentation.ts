// Next.js instrumentation hook — runs once per server process.
// This is the canonical place to initialize server-side LaunchDarkly
// observability so that SSR (app router), getServerSideProps (pages router),
// middleware and route handlers all produce OpenTelemetry spans/exporter data.
export async function register() {
	// The OTel-based node SDK is Node-only; guard the runtime explicitly.
	if (process.env.NEXT_RUNTIME !== 'nodejs') return

	const { registerObservability } =
		await import('@launchdarkly/observability-next/server')
	const { CONSTANTS } = await import('@/constants')

	await registerObservability({
		sdkKey: CONSTANTS.LAUNCHDARKLY_SDK_KEY,
		serviceName: CONSTANTS.BACKEND_SERVICE_NAME,
		environment: CONSTANTS.OBSERVE_ENVIRONMENT,
		// Optional staging/local overrides. The LAUNCHDARKLY_* vars are read by
		// the node SDK's defaults too, but be explicit here so whichever env file
		// this app runs with is the single source of truth.
		...(process.env.LAUNCHDARKLY_OTEL_ENDPOINT
			? { otlpEndpoint: process.env.LAUNCHDARKLY_OTEL_ENDPOINT }
			: {}),
		...(process.env.LAUNCHDARKLY_BACKEND_URL
			? { backendUrl: process.env.LAUNCHDARKLY_BACKEND_URL }
			: {}),
	})
}
