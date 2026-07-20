// Single source of truth for the JS observability `service.version`.
//
// This example uses the Symbols Id Lane, where the backend matches an uploaded
// source map to an error by the symbols id the app reports — not by this
// version — so `service.version` does NOT need to stay in sync with any
// `ldcli` flag. It's still reported on every signal and embedded in the demo
// error below to make builds easy to tell apart. See README "Uploading React
// Native symbols".
//
// Kept in its own module (rather than App.tsx) to avoid an import cycle: App.tsx
// imports the screens, and the screens embed this version in demo errors.
export const SERVICE_VERSION = '1.1.0';
