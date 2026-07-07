// Single source of truth for which LaunchDarkly instance this example talks to.
//
// Every endpoint the app uses is bundled here per environment so they can never
// drift apart: the LaunchDarkly client-side URLs (streamUri/baseUri/eventsUri,
// used by the online JS ReactNativeLDClient for flag streaming + identify) and
// the observability URLs (otlpEndpoint/backendUrl, forwarded to the JS
// observability plugin and the native session replay modules for trace/log/
// replay upload). Pick the environment once via LAUNCHDARKLY_ENV in .env; the
// mobile key is the only value set separately, since it is a per-environment
// secret that cannot be derived.
//
// A mismatch (e.g. a staging mobile key streaming against production) surfaces
// as a LaunchDarklyStreamingError with code 401, which makes identify resolve
// with status 'error' and silently drops all downstream identify telemetry.

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

export function resolveLDEnvironment(
  name: string | undefined,
): { env: LDEnvironmentName; endpoints: LDEndpoints } {
  const env: LDEnvironmentName =
    name === 'staging' || name === 'production' ? name : 'production';
  if (name && name !== env) {
    console.warn(
      `[env] unknown LAUNCHDARKLY_ENV="${name}", falling back to "production"`,
    );
  }
  return { env, endpoints: LD_ENVIRONMENTS[env] };
}
