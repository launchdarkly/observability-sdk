# Change log

All notable changes to the LaunchDarkly Observability SDK for Flutter will be documented in this file. This project adheres to [Semantic Versioning](https://semver.org).

## [0.12.1](https://github.com/launchdarkly/observability-sdk/compare/launchdarkly_flutter_observability-0.12.0...launchdarkly_flutter_observability-0.12.1) (2026-06-19)


### Bug Fixes

* **flutter:** gate Android touch capture on analytics.taps ([#635](https://github.com/launchdarkly/observability-sdk/issues/635)) ([394d8a7](https://github.com/launchdarkly/observability-sdk/commit/394d8a7e15b62dd937ae5f3cdbfb63ed81672d2b))

## [0.12.0](https://github.com/launchdarkly/observability-sdk/compare/launchdarkly_flutter_observability-0.11.0...launchdarkly_flutter_observability-0.12.0) (2026-06-18)


### Features

* richer click analytics (ldId, screen_id/screen_name) + touch-capture gating + lifecycle span cleanup ([#634](https://github.com/launchdarkly/observability-sdk/issues/634)) ([91f5816](https://github.com/launchdarkly/observability-sdk/commit/91f5816d0db59cc55f33c651a3c7eab806208c73))

## [0.11.0](https://github.com/launchdarkly/observability-sdk/compare/launchdarkly_flutter_observability-0.10.0...launchdarkly_flutter_observability-0.11.0) (2026-06-17)


### Features

* **flutter:** configurable Session Replay capture scale ([#626](https://github.com/launchdarkly/observability-sdk/issues/626)) ([e3d8383](https://github.com/launchdarkly/observability-sdk/commit/e3d83830edcefb8b82019099d0d0bd924f5e5b6f))

## [0.10.0](https://github.com/launchdarkly/observability-sdk/compare/launchdarkly_flutter_observability-0.9.0...launchdarkly_flutter_observability-0.10.0) (2026-06-15)


### Features

* **flutter:** allow overriding OTLP endpoint and backend URL in example ([#624](https://github.com/launchdarkly/observability-sdk/issues/624)) ([7eff0cd](https://github.com/launchdarkly/observability-sdk/commit/7eff0cdacd029995fda822c6c95e4d3645fed01c))

## [0.9.0](https://github.com/launchdarkly/observability-sdk/compare/launchdarkly_flutter_observability-0.8.0...launchdarkly_flutter_observability-0.9.0) (2026-06-12)


### Features

* **android:** app_launch span + Session Replay Launch breadcrumb ([#620](https://github.com/launchdarkly/observability-sdk/issues/620)) ([1822333](https://github.com/launchdarkly/observability-sdk/commit/18223336dcf2ec19cb38658e5c299c0c043c5c72))
* **flutter:** propagate appLifecycle + appLaunch analytics options ([#621](https://github.com/launchdarkly/observability-sdk/issues/621)) ([3cb99be](https://github.com/launchdarkly/observability-sdk/commit/3cb99be9ef92af0d1ded32aab00f7d042986c7c7))

## [0.8.0](https://github.com/launchdarkly/observability-sdk/compare/launchdarkly_flutter_observability-0.7.0...launchdarkly_flutter_observability-0.8.0) (2026-06-12)


### Features

* **flutter:** forward identify to native observability + Session Replay ([#617](https://github.com/launchdarkly/observability-sdk/issues/617)) ([8384b4e](https://github.com/launchdarkly/observability-sdk/commit/8384b4e63d366b0a78b2134f98ea47a8e0040f36))

## [0.7.0](https://github.com/launchdarkly/observability-sdk/compare/launchdarkly_flutter_observability-0.6.0...launchdarkly_flutter_observability-0.7.0) (2026-06-11)


### Features

* Flutter track API (LDObserve.track + afterTrack hook) ([#612](https://github.com/launchdarkly/observability-sdk/issues/612)) ([4d58af4](https://github.com/launchdarkly/observability-sdk/commit/4d58af408f3dcfc181b23c67350aa761a59db2b8))

## [0.6.0](https://github.com/launchdarkly/observability-sdk/compare/launchdarkly_flutter_observability-0.5.0...launchdarkly_flutter_observability-0.6.0) (2026-06-09)


### Features

* Flutter mask stabilization + Native Logs/Spans ([#597](https://github.com/launchdarkly/observability-sdk/issues/597)) ([7e5cfd2](https://github.com/launchdarkly/observability-sdk/commit/7e5cfd29831faea3f04ab083338b15ec05a07489))

## [0.5.0](https://github.com/launchdarkly/observability-sdk/compare/launchdarkly_flutter_observability-0.4.0...launchdarkly_flutter_observability-0.5.0) (2026-06-04)


### Features

* MAUI  API revamp ([#573](https://github.com/launchdarkly/observability-sdk/issues/573)) ([c2dfa64](https://github.com/launchdarkly/observability-sdk/commit/c2dfa64a55aad59af02002401a3b004852e15b20))

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
