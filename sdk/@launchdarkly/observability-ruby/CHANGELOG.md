# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

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
