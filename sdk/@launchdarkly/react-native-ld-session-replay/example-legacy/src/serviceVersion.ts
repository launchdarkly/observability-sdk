// Single source of truth for the JS observability `service.version`. It must
// match the `--app-version` passed to `ldcli symbols upload` (see README), so
// the backend can find the uploaded source map for a symbolicated stack trace.
// Kept in its own module (rather than App.tsx) to avoid an import cycle: App.tsx
// imports the screens, and the screens embed this version in demo errors.
export const SERVICE_VERSION = '1.0.9';
