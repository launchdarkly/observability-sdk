export const SDK_NAME = '@launchdarkly/observability-next'

// Keep in sync with package.json "version". Used only as informational
// telemetry metadata (telemetry.sdk.version), so minor drift is harmless. It is
// a literal (rather than an import of package.json) so the manifest is not
// bundled into the browser client.
export const SDK_VERSION = '0.1.0'
