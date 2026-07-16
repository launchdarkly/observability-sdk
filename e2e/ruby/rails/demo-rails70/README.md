# demo-rails70 — legacy Rails observability regression repro

A legacy Rails application that pins the LaunchDarkly observability plugin to a
known-broken scenario and guards against it regressing. The historical directory
name is retained to avoid path churn, while Rails is constrained to the oldest
safe 7.1 release line.

## Why this exists

The plugin used to depend on `opentelemetry-instrumentation-all`, whose
Rails-family members could fail their runtime `compatible?` checks and never
attach. This app keeps dedicated coverage for the individually pinned
Rails-family instrumentations on an older supported Rails release.

Ruby stays at **3.3.4** on purpose: on a current Ruby, Bundler still resolves the
*latest* instrumentation gems, faithfully matching a real Rails 7.0 customer. (On
Ruby < 3.2, Bundler would self-heal to an older instrumentation set via
`required_ruby_version` and the bug would not reproduce.)

## How the telemetry assertion works

The OTLP exporter is pointed at a small **in-process OTLP sink**
([`test/support/otlp_sink.rb`](test/support/otlp_sink.rb)) via
`OTEL_EXPORTER_OTLP_ENDPOINT`. The sink decodes the real OTLP protobuf using the
proto classes shipped with the exporter gems, so the test asserts that traces (an
auto-instrumented HTTP **server** span), a log record, and a captured exception
are actually **exported over the wire** — in pure Ruby, no Docker or Node, under
`bundle exec rake`.

`test/integration/otlp_export_e2e_test.rb` is the headline test:

- **Without compatible Rails-family instrumentation**: no server span is
  produced or exported — the test fails.
- **With instrumentation-rails pinned to 0.41**: the server span,
  log, and exception all reach the sink — the test passes.

## Running it

```bash
cd e2e/ruby/rails/demo-rails70
bundle install
bundle exec rake          # runs the test suite (this is what CI runs)
```

Requires Ruby 3.3.x. The suite needs no external services: the OTLP sink runs
in-process and a dummy `LAUNCHDARKLY_SDK_KEY` is set by `test/test_helper.rb`.

In CI this app runs as the `e2e-rails-legacy` job in
[`.github/workflows/ruby-plugin.yml`](../../../../.github/workflows/ruby-plugin.yml).
