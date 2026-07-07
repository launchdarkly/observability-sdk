declare module '@env' {
  // Per-environment secret; cannot be derived, so it is set separately.
  export const LAUNCHDARKLY_MOBILE_KEY: string;
  // Single source of truth for which LaunchDarkly instance to target. One of
  // 'production' | 'staging'; selects the full endpoint bundle in
  // src/ldEnvironments.ts (both LD client URLs and observability URLs).
  export const LAUNCHDARKLY_ENV: string;
}
