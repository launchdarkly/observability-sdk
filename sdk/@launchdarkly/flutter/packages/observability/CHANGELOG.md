# Change log

All notable changes to the LaunchDarkly Observability SDK for Flutter will be documented in this file. This project adheres to [Semantic Versioning](https://semver.org).

## [0.4.0](https://github.com/launchdarkly/observability-sdk/compare/launchdarkly_flutter_observability-0.3.0...launchdarkly_flutter_observability-0.4.0) (2026-06-03)


### Features

* Android - Rename track span event  ([#586](https://github.com/launchdarkly/observability-sdk/issues/586)) ([2f5b066](https://github.com/launchdarkly/observability-sdk/commit/2f5b0667385b59181d3e1970234fc7bcae08aa19))

## [0.3.0](https://github.com/launchdarkly/observability-sdk/compare/launchdarkly_flutter_observability-0.2.0...launchdarkly_flutter_observability-0.3.0) (2026-06-03)


### Features

* **flutter:** propagate native-only observability options ([#584](https://github.com/launchdarkly/observability-sdk/issues/584)) ([51a0658](https://github.com/launchdarkly/observability-sdk/commit/51a065874986b7574ddcb4410d3fd0b8e006f8dd))

## [0.2.0](https://github.com/launchdarkly/observability-sdk/compare/launchdarkly_flutter_observability-0.1.0...launchdarkly_flutter_observability-0.2.0) (2026-06-02)


### Features

* **@launchdarkly/session-replay-react-native:** use cocoapods for native session replay ([#434](https://github.com/launchdarkly/observability-sdk/issues/434)) ([41988e1](https://github.com/launchdarkly/observability-sdk/commit/41988e196b02901964efdad0e25f892d5ba55fc4))
* Android - support SurfaceView ([#563](https://github.com/launchdarkly/observability-sdk/issues/563)) ([f142c1e](https://github.com/launchdarkly/observability-sdk/commit/f142c1e857880713aa6312e23ee7e4aea545700a))
* Flutter API and package publish ([#578](https://github.com/launchdarkly/observability-sdk/issues/578)) ([7f82771](https://github.com/launchdarkly/observability-sdk/commit/7f827717e833dc108fc0f77d19b82a8291f5909a))

## 0.1.0

Initial early-access release of `launchdarkly_flutter_observability`.

### Features

* Unified `LDObserve` facade for observability (spans, logs, errors) and session replay.
* Cross-platform Dart OpenTelemetry pipeline (mobile and web).
* Native session replay capture on iOS and Android via `SessionReplayCapture`.
* Automatic instrumentation: HTTP requests, crash/error reporting, feature flag evaluation correlation, app lifecycle/launch times, and `print`/`debugPrint` capture.
