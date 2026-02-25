# LaunchDarkly Observability — Sinatra Demo

A minimal [Sinatra](https://sinatrarb.com/) app that demonstrates the
[LaunchDarkly Observability Ruby SDK](../../../../sdk/@launchdarkly/observability-ruby/).

It wires up the observability plugin and request-tracing middleware, then
serves an HTML dashboard for exercising traces, logs, errors, and feature
flag evaluations.

## Prerequisites

- Ruby 3.3.4 (see `.ruby-version` / `.tool-versions`)
- Bundler

## Setup

```bash
bundle install
```

## Environment variables

| Variable | Required | Default | Description |
|----------|----------|---------|-------------|
| `LAUNCHDARKLY_SDK_KEY` | Yes | — | Your LaunchDarkly SDK key |
| `OTEL_EXPORTER_OTLP_ENDPOINT` | No | `https://otel.observability.app.launchdarkly.com:4318` | OTLP collector endpoint (override for local dev) |

## Running

```bash
# Via rackup (uses config.ru)
bundle exec rackup -p 4567

# Or directly
bundle exec ruby app.rb
```

The app listens on `http://localhost:4567` by default.

## Routes

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/` | HTML dashboard with action buttons and flag table |
| `POST` | `/traces` | Creates nested OpenTelemetry spans |
| `POST` | `/logs` | Writes string and structured log entries |
| `POST` | `/errors` | Triggers a `ZeroDivisionError` and records it via `record_exception` |
| `POST` | `/flags/evaluate` | Evaluates a single flag (`{ "flag_key": "..." }`) |
| `GET` | `/flags` | Returns all flag values as JSON |
