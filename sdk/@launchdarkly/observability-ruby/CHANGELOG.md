# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## Unreleased

### Bug Fixes

* **ruby:** keep OpenTelemetry auto-instrumentation working on Rails 7.0. The plugin now depends on individual `opentelemetry-instrumentation-*` gems instead of `opentelemetry-instrumentation-all`, pinning the Rails-family instrumentations below the releases that require Rails 7.1 (so Rails 7.0 keeps working) while every other instrumentation tracks the latest. Instrumentations that cannot attach now produce a single actionable warning instead of a flurry of "failed to install" lines.

## [0.2.2](https://github.com/launchdarkly/observability-sdk/compare/launchdarkly-observability-ruby/0.2.1...launchdarkly-observability-ruby/0.2.2) (2026-06-22)


### Bug Fixes

* **ruby:** omit nil feature_flag.context.id for invalid contexts ([#641](https://github.com/launchdarkly/observability-sdk/issues/641)) ([587e2e4](https://github.com/launchdarkly/observability-sdk/commit/587e2e40764bade29198c47e6746887f1af6d856))

## [0.2.1](https://github.com/launchdarkly/observability-sdk/compare/launchdarkly-observability-ruby/0.2.0...launchdarkly-observability-ruby/0.2.1) (2026-06-02)


### Bug Fixes

* **ruby:** observability plugin compatibility — require server-sdk &gt;= 8.11.0 and fix Rails Railtie load order ([#575](https://github.com/launchdarkly/observability-sdk/issues/575)) ([e9d8310](https://github.com/launchdarkly/observability-sdk/commit/e9d8310c0d0d53c20ee8a48f31279a386988d27a))

## [0.2.0](https://github.com/launchdarkly/observability-sdk/compare/launchdarkly-observability-ruby-0.1.0...launchdarkly-observability-ruby/0.2.0) (2026-03-27)


### Features

* **@launchdarkly/session-replay-react-native:** use cocoapods for native session replay ([#434](https://github.com/launchdarkly/observability-sdk/issues/434)) ([41988e1](https://github.com/launchdarkly/observability-sdk/commit/41988e196b02901964efdad0e25f892d5ba55fc4))
* **observability-ruby:** publish Ruby observability plugin gem ([#413](https://github.com/launchdarkly/observability-sdk/issues/413)) ([569a7b4](https://github.com/launchdarkly/observability-sdk/commit/569a7b441cad969ef038a69629021d68e7e23a4f))
* ruby observability plugin ([#360](https://github.com/launchdarkly/observability-sdk/issues/360)) ([79dc8dd](https://github.com/launchdarkly/observability-sdk/commit/79dc8ddba1d2134f28323cfccdb12f2f6cd13628))


### Bug Fixes

* structured stacktrace capture in Ruby observability plugin ([#427](https://github.com/launchdarkly/observability-sdk/issues/427)) ([1dae61e](https://github.com/launchdarkly/observability-sdk/commit/1dae61e5f7b53866965726c1128cdfdb076b147e))

## [0.1.0] - 2026-02-03

### Added

- Initial release of LaunchDarkly Observability Plugin for Ruby SDK
- OpenTelemetry-based tracing for flag evaluations
- Rails integration with Railtie and Rack middleware
- Support for traces, logs, and metrics export via OTLP
- Auto-instrumentation for Rails, ActiveRecord, Net::HTTP, and more
- Context propagation between HTTP requests and flag evaluations
