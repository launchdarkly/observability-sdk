/**
 * Shared constants describing the optional same-origin proxy that
 * {@link withLaunchDarklyConfig} sets up and that the client reads. Proxying
 * routes browser telemetry through your own domain so it is not blocked by ad
 * blockers and so that requests share the page's origin.
 */

/** Env var (inlined by Next) signalling that the proxy rewrites are configured. */
export const PROXY_ENV_FLAG = 'configureLaunchDarklyProxy'

/** Same-origin path that proxies to the LaunchDarkly events ingest endpoint. */
export const PROXY_BACKEND_PATH = '/highlight-events'

/** Public LaunchDarkly production endpoints that the proxy rewrites forward to. */
export const LD_PUBLIC_BACKEND_URL =
	'https://pub.observability.app.launchdarkly.com'
export const LD_OTLP_ENDPOINT =
	'https://otel.observability.app.launchdarkly.com'

/** Public LaunchDarkly staging endpoints (mirrors the mobile example apps). */
export const LD_STAGING_BACKEND_URL =
	'https://pub.observability.ld-stg.launchdarkly.com'
export const LD_STAGING_OTLP_ENDPOINT =
	'https://otel.observability.ld-stg.launchdarkly.com:4318'

/**
 * Env var selecting the LaunchDarkly deployment to send data to ('production'
 * by default, 'staging' switches the proxy destinations to the staging hosts).
 */
export const LAUNCHDARKLY_ENV_ENV_VAR = 'LAUNCHDARKLY_ENV'

/** Optional env var overriding the LaunchDarkly events ingest (backend) URL. */
export const LAUNCHDARKLY_BACKEND_URL_ENV_VAR = 'LAUNCHDARKLY_BACKEND_URL'

/** Optional env var overriding the LaunchDarkly OTLP endpoint for traces/logs/metrics. */
export const LAUNCHDARKLY_OTEL_ENDPOINT_ENV_VAR = 'LAUNCHDARKLY_OTEL_ENDPOINT'

export interface LDEndpoints {
	/** LaunchDarkly public events ingest URL (session/error/log data). */
	backendUrl: string
	/** LaunchDarkly OTLP-over-HTTP endpoint for traces, metrics and logs. */
	otlpEndpoint: string
}

/**
 * Resolve the LaunchDarkly endpoints that the proxy rewrites forward to.
 *
 * Precedence: explicit options > `LAUNCHDARKLY_OTEL_ENDPOINT` /
 * `LAUNCHDARKLY_BACKEND_URL` env vars > the 'staging' hosts when
 * `LAUNCHDARKLY_ENV=staging` > the production hosts. This lets an app (most
 * commonly an example / e2e app) repoint the same-origin proxy at a staging or
 * local backend via env vars without code changes.
 */
export function resolveLDEndpoints(
	opts: {
		backendUrl?: string
		otelEndpoint?: string
	} = {},
): LDEndpoints {
	const isStaging =
		(process.env[LAUNCHDARKLY_ENV_ENV_VAR] ?? '').toLowerCase() ===
		'staging'

	const backendUrl =
		opts.backendUrl ||
		process.env[LAUNCHDARKLY_BACKEND_URL_ENV_VAR] ||
		(isStaging ? LD_STAGING_BACKEND_URL : LD_PUBLIC_BACKEND_URL)

	const otlpEndpoint =
		opts.otelEndpoint ||
		process.env[LAUNCHDARKLY_OTEL_ENDPOINT_ENV_VAR] ||
		(isStaging ? LD_STAGING_OTLP_ENDPOINT : LD_OTLP_ENDPOINT)

	return { backendUrl, otlpEndpoint }
}
