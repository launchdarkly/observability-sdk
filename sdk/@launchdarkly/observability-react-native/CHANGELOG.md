# Changelog

## [0.16.0](https://github.com/launchdarkly/observability-sdk/compare/observability-react-native-0.15.0...observability-react-native-0.16.0) (2026-07-17)


### Features

* **observability:** expose configurable telemetry buffer options (React Native) ([#679](https://github.com/launchdarkly/observability-sdk/issues/679)) ([96db586](https://github.com/launchdarkly/observability-sdk/commit/96db5862923fda6da6fa3ebdc4727316eebf231f))

## [0.15.0](https://github.com/launchdarkly/observability-sdk/compare/observability-react-native-0.14.0...observability-react-native-0.15.0) (2026-07-16)


### Features

* **observability:** dedupe feature flag exposures within a time window (web + React Native) ([#676](https://github.com/launchdarkly/observability-sdk/issues/676)) ([c34b390](https://github.com/launchdarkly/observability-sdk/commit/c34b390d68ff642e13f2863d1aa3f2732c61b70b))

## [0.14.0](https://github.com/launchdarkly/observability-sdk/compare/observability-react-native-0.13.0...observability-react-native-0.14.0) (2026-07-08)


### Features

* add app_reload event with session preservation across reloads (React Native) ([#670](https://github.com/launchdarkly/observability-sdk/issues/670)) ([518bb22](https://github.com/launchdarkly/observability-sdk/commit/518bb225df0d06d7d5bf7029f38b60125e7b7624))


### Bug Fixes

* use secure RNG for React Native session ids ([#668](https://github.com/launchdarkly/observability-sdk/issues/668)) ([2761a2d](https://github.com/launchdarkly/observability-sdk/commit/2761a2dd090680b2a751a183fbcf2a4397670caf))

## [0.13.0](https://github.com/launchdarkly/observability-sdk/compare/observability-react-native-0.12.0...observability-react-native-0.13.0) (2026-06-29)


### Features

* **observability-react-native:** expose LDTracer with withSpan via getTracer() ([#658](https://github.com/launchdarkly/observability-sdk/issues/658)) ([36a622a](https://github.com/launchdarkly/observability-sdk/commit/36a622a6ab2878ac7b262b51cbf557ebdba57271))

## [0.12.0](https://github.com/launchdarkly/observability-sdk/compare/observability-react-native-0.11.0...observability-react-native-0.12.0) (2026-06-29)


### Features

* add GraphQL operation attributes to instrumented spans ([#644](https://github.com/launchdarkly/observability-sdk/issues/644)) ([4772f5e](https://github.com/launchdarkly/observability-sdk/commit/4772f5e938f0020e43c0d5725ca879dc2476041d))

## [0.11.0](https://github.com/launchdarkly/observability-sdk/compare/observability-react-native-0.10.0...observability-react-native-0.11.0) (2026-06-26)


### Features

* **observability-react-native:** accept plain nested dictionaries in track ([#650](https://github.com/launchdarkly/observability-sdk/issues/650)) ([f34ec69](https://github.com/launchdarkly/observability-sdk/commit/f34ec69ab830c9486705dc68247a5f50b114accb))

## [0.10.0](https://github.com/launchdarkly/observability-sdk/compare/observability-react-native-0.9.3...observability-react-native-0.10.0) (2026-06-25)


### Features

* supporting advanced trace cases ([#645](https://github.com/launchdarkly/observability-sdk/issues/645)) ([dac45eb](https://github.com/launchdarkly/observability-sdk/commit/dac45eb6e61c63f954da1ec63aec58764bc810e1))

## [0.9.3](https://github.com/launchdarkly/observability-sdk/compare/observability-react-native-0.9.2...observability-react-native-0.9.3) (2026-06-05)


### Bug Fixes

* strip URL fragments from sanitizeUrl to prevent OAuth token leaks ([#595](https://github.com/launchdarkly/observability-sdk/issues/595)) ([4925031](https://github.com/launchdarkly/observability-sdk/commit/49250315ec48aef5322a0cd9f5363e6cdd476c37))
* upgrade vulnerable dependencies flagged by Dependabot ([#596](https://github.com/launchdarkly/observability-sdk/issues/596)) ([c15b3d9](https://github.com/launchdarkly/observability-sdk/commit/c15b3d9547946f6dfe334f0a5b43002f5f8781a2))

## [0.9.2](https://github.com/launchdarkly/observability-sdk/compare/observability-react-native-0.9.1...observability-react-native-0.9.2) (2026-06-03)


### Bug Fixes

* update vite and vitest to resolve security vulnerabilities ([#589](https://github.com/launchdarkly/observability-sdk/issues/589)) ([4538396](https://github.com/launchdarkly/observability-sdk/commit/45383961bdd2d07caa7804034dda79cf42a8a8f4))

## [0.9.1](https://github.com/launchdarkly/observability-sdk/compare/observability-react-native-0.9.0...observability-react-native-0.9.1) (2026-04-24)


### Bug Fixes

* scope tracingOrigins matching to the URL origin ([#502](https://github.com/launchdarkly/observability-sdk/issues/502)) ([f12201c](https://github.com/launchdarkly/observability-sdk/commit/f12201caa4dcecd31cde439958c9c31adef7b36f))

## [0.9.0](https://github.com/launchdarkly/observability-sdk/compare/observability-react-native-0.8.1...observability-react-native-0.9.0) (2026-04-22)


### Features

* **react-native:** Add `NetworkRecordingOptions` and network sanitizer ([#485](https://github.com/launchdarkly/observability-sdk/issues/485)) ([b717bcb](https://github.com/launchdarkly/observability-sdk/commit/b717bcbd7588045629e284d1ec9d8996edd4287d))

## [0.8.1](https://github.com/launchdarkly/observability-sdk/compare/observability-react-native-0.8.0...observability-react-native-0.8.1) (2026-04-22)


### Bug Fixes

* **deps:** address dependabot security alerts across SDK manifests ([#478](https://github.com/launchdarkly/observability-sdk/issues/478)) ([02f6a7c](https://github.com/launchdarkly/observability-sdk/commit/02f6a7ce6c5d5dbb22f8cde81647c3e4deb05ab6))

## [0.8.0](https://github.com/launchdarkly/observability-sdk/compare/observability-react-native-0.7.1...observability-react-native-0.8.0) (2026-03-26)


### Features

* **@launchdarkly/session-replay-react-native:** use cocoapods for native session replay ([#434](https://github.com/launchdarkly/observability-sdk/issues/434)) ([41988e1](https://github.com/launchdarkly/observability-sdk/commit/41988e196b02901964efdad0e25f892d5ba55fc4))

## [0.7.1](https://github.com/launchdarkly/observability-sdk/compare/observability-react-native-0.7.0...observability-react-native-0.7.1) (2026-03-05)


### Bug Fixes

* ldclient dependencies ([#407](https://github.com/launchdarkly/observability-sdk/issues/407)) ([65a5e6a](https://github.com/launchdarkly/observability-sdk/commit/65a5e6a1999c9e66c7f4011f512d17de256f919c))

## [0.7.0](https://github.com/launchdarkly/observability-sdk/compare/observability-react-native-0.6.0...observability-react-native-0.7.0) (2025-10-15)


### Features

* update unhandled promise rejection handling ([#260](https://github.com/launchdarkly/observability-sdk/issues/260)) ([bd0321c](https://github.com/launchdarkly/observability-sdk/commit/bd0321cd2b0edf9e0556dd914ff97430890b0564))

## [0.6.0](https://github.com/launchdarkly/observability-sdk/compare/observability-react-native-0.5.0...observability-react-native-0.6.0) (2025-09-02)


### Features

* improve exception instrumentation in react-native SDK ([#177](https://github.com/launchdarkly/observability-sdk/issues/177)) ([22b2be7](https://github.com/launchdarkly/observability-sdk/commit/22b2be7ad83a206ba4e630c7787debf14e72c4a5))

## [0.5.0](https://github.com/launchdarkly/observability-sdk/compare/observability-react-native-0.4.0...observability-react-native-0.5.0) (2025-08-29)


### Features

* Add sampling support to React Native observability plugin ([#108](https://github.com/launchdarkly/observability-sdk/issues/108)) ([1beae4d](https://github.com/launchdarkly/observability-sdk/commit/1beae4d39e2efe31f60d8d7712f7766984a29d81))
* Add TypeDoc setup for @launchdarkly/observability-react-native ([#133](https://github.com/launchdarkly/observability-sdk/issues/133)) ([f523fbf](https://github.com/launchdarkly/observability-sdk/commit/f523fbfc036587d3a9303910049184b13cf92d43))
* Update instrumentation configuration for ease of use. ([#112](https://github.com/launchdarkly/observability-sdk/issues/112)) ([011032f](https://github.com/launchdarkly/observability-sdk/commit/011032f7c2cb941bcde5f8225705a129f6c2019c))


### Bug Fixes

* Correct canonical key ID generation. ([#188](https://github.com/launchdarkly/observability-sdk/issues/188)) ([7e8f9e5](https://github.com/launchdarkly/observability-sdk/commit/7e8f9e58c402d344791647c13b6df14e899d0095))

## 0.4.0

### Minor Changes

- 7e8f9e5: Add support for specifying a contextFriendlyName function.
- 7e8f9e5: Fix generation of canonical key.

## 0.3.0

### Minor Changes

- 1beae4d: Add sampling support to React Native observability plugin

### Patch Changes

- 1beae4d: add sampling support to react native o11y plugin

## 0.2.1

### Patch Changes

- 011032f: Update OTEL dependencies for React Native Plugin

## 0.2.0

### Minor Changes

- ece4ed3: initial release of RN observability plugin

### Patch Changes

- cda2e4a: add tracingOrigins and urlBlocklist and improve span naming

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.1.0] - 2025-07-08

### Added

- Initial release of @launchdarkly/observability-react-native
- OpenTelemetry instrumentation for React Native applications
- Automatic tracing with fetch instrumentation
- Structured logging with console instrumentation
- Metrics collection for performance monitoring
- Error tracking with stack traces
- Session management with device context
- LaunchDarkly plugin integration
- Comprehensive configuration options
- Support for manual instrumentation via LDObserve API
