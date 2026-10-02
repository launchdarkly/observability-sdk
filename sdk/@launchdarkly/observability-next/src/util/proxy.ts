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

/** Public LaunchDarkly endpoints that the proxy rewrites forward to. */
export const LD_PUBLIC_BACKEND_URL =
	'https://pub.observability.app.launchdarkly.com'
export const LD_OTLP_ENDPOINT =
	'https://otel.observability.app.launchdarkly.com'
