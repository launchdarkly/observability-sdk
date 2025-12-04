# Changelog

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
