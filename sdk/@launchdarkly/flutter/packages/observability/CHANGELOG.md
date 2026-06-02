# Change log

All notable changes to the LaunchDarkly Observability SDK for Flutter will be documented in this file. This project adheres to [Semantic Versioning](https://semver.org).

## 0.1.0

Initial early-access release of `launchdarkly_flutter_observability`.

### Features

* Unified `LDObserve` facade for observability (spans, logs, errors) and session replay.
* Cross-platform Dart OpenTelemetry pipeline (mobile and web).
* Native session replay capture on iOS and Android via `SessionReplayCapture`.
* Automatic instrumentation: HTTP requests, crash/error reporting, feature flag evaluation correlation, app lifecycle/launch times, and `print`/`debugPrint` capture.
