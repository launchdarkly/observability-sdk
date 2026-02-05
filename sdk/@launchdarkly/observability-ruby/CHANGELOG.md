# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.1.0] - 2024-02-03

### Added

- Initial release of LaunchDarkly Observability Plugin for Ruby SDK
- OpenTelemetry-based tracing for flag evaluations
- Rails integration with Railtie and Rack middleware
- Support for traces, logs, and metrics export via OTLP
- Auto-instrumentation for Rails, ActiveRecord, Net::HTTP, and more
- Context propagation between HTTP requests and flag evaluations
