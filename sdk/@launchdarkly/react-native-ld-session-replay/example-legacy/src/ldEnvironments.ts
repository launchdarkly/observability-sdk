// Single source of truth for which LaunchDarkly instance this example talks to.
//
// LAUNCHDARKLY_ENV selects a bundle of the LaunchDarkly client-side URLs
// (streamUri/baseUri/eventsUri, used by the online JS ReactNativeLDClient for
// flag streaming + identify). These must stay paired with the mobile key: a
// mismatch (e.g. a staging mobile key streaming against production) surfaces as
// a LaunchDarklyStreamingError with code 401, which makes identify resolve with
// status 'error' and silently drops all downstream identify telemetry.
//
// The observability URLs (otlpEndpoint/backendUrl, forwarded to the JS
// observability plugin and the native session replay modules for trace/log/
// replay upload) default to the same bundle, but can be overridden
// independently via LAUNCHDARKLY_OTLP_ENDPOINT / LAUNCHDARKLY_BACKEND_URL in
// .env. This lets you point observability at localhost or an arbitrary staging
// server while still streaming flags from a real LaunchDarkly instance.

export type LDEnvironmentName = 'production' | 'staging';

export interface LDEndpoints {
  // LaunchDarkly client-side SDK endpoints (online JS client only).
  streamUri: string;
  baseUri: string;
  eventsUri: string;
  // Observability endpoints (JS observability plugin + native session replay).
  otlpEndpoint: string;
  backendUrl: string;
}

export const LD_ENVIRONMENTS: Record<LDEnvironmentName, LDEndpoints> = {
  production: {
    streamUri: 'https://clientstream.launchdarkly.com',
    baseUri: 'https://clientsdk.launchdarkly.com',
    eventsUri: 'https://events.launchdarkly.com',
    otlpEndpoint: 'https://otel.observability.app.launchdarkly.com:4318',
    backendUrl: 'https://pub.observability.app.launchdarkly.com/',
  },
  staging: {
    // NOTE: confirm the exact staging streaming host. baseUri/eventsUri match
    // the repo's web staging config (ld-stg.launchdarkly.com / events-stg…).
    streamUri: 'https://clientstream.ld-stg.launchdarkly.com',
    baseUri: 'https://ld-stg.launchdarkly.com',
    eventsUri: 'https://events-stg.launchdarkly.com',
    otlpEndpoint: 'https://otel.observability.ld-stg.launchdarkly.com:4318',
    backendUrl: 'https://pub.observability.ld-stg.launchdarkly.com/',
  },
};

// Optional per-endpoint overrides for the observability URLs. Anything left
// unset (or blank) falls back to the selected environment's bundled default.
export interface LDObservabilityOverrides {
  otlpEndpoint?: string;
  backendUrl?: string;
}

export function resolveLDEnvironment(
  name: string | undefined,
  overrides: LDObservabilityOverrides = {},
): {env: LDEnvironmentName; endpoints: LDEndpoints} {
  const env: LDEnvironmentName =
    name === 'staging' || name === 'production' ? name : 'production';
  if (name && name !== env) {
    console.warn(
      `[env] unknown LAUNCHDARKLY_ENV="${name}", falling back to "production"`,
    );
  }

  const base = LD_ENVIRONMENTS[env];
  const otlpEndpoint = overrides.otlpEndpoint?.trim() || base.otlpEndpoint;
  const backendUrl = overrides.backendUrl?.trim() || base.backendUrl;

  return {
    env,
    endpoints: {...base, otlpEndpoint, backendUrl},
  };
}
