// `react-native-url-polyfill` ships ESM and pulls in `whatwg-url-without-unicode`
// plus React Native native modules (BlobModule), which Jest cannot load in the
// node test environment. The polyfill is a pure runtime side-effect (installing a
// spec-compliant global `URL` for the OTLP exporter), so a no-op stub is safe for
// unit tests that only exercise the session-replay plugin logic.
module.exports = {
  setupURLPolyfill: () => {},
  URL: globalThis.URL,
  URLSearchParams: globalThis.URLSearchParams,
};
