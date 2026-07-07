declare module '@env' {
  // Per-environment secret; cannot be derived, so it is set separately.
  export const LAUNCHDARKLY_MOBILE_KEY: string;
  // Selects which LaunchDarkly instance to target for the client-side URLs
  // (flag streaming + identify). One of 'production' | 'staging'; the bundle
  // lives in src/ldEnvironments.ts. Must stay paired with the mobile key.
  export const LAUNCHDARKLY_ENV: string;
  // Optional overrides for the observability endpoints (traces/logs + session
  // replay upload). When unset, they default to the LAUNCHDARKLY_ENV bundle.
  // Set these to target localhost or an arbitrary staging server.
  export const LAUNCHDARKLY_OTLP_ENDPOINT: string;
  export const LAUNCHDARKLY_BACKEND_URL: string;
}
