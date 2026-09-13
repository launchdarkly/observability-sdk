# Flask App

A basic flask app using LaunchDarkly and the LaunchDarkly Observability Plugin.

# Quick Start

The `@launchdarkly/observability-python` package must be built before this project can be ran.

Set a LaunchDarkly SDK key:
```bash
export LAUNCHDARKLY_SDK_KEY=<your-sdk-key>
```

Install dependencies:
```bash
make install
```

Run the application:
```bash
make run
```

# Pointing at a local stack

The SDK exports to LaunchDarkly by default. To send to a local observability
stack, point it at the local collector — OTLP over gRPC, port 4317:

```bash
export OTEL_EXPORTER_OTLP_ENDPOINT=http://localhost:4317
```

Data is attributed to the project the SDK key belongs to, so use a key from the
environment the data should land in.

# Routes

| Route | Exercises |
| --- | --- |
| `/` | Nothing; a liveness check |
| `/crash`, `/raise-exception` | An uncaught exception, recorded by the Flask instrumentation |
| `/manual-record-exception` | `observe.record_exception`, including source context |
| `/manual-record-chained-exception` | A `raise ... from` chain, whose root cause frames and source must survive |
| `/manual-span` | `observe.start_span` and a flag evaluation |
| `/manual-record-log` | `observe.record_log` |
| `/record-metrics` | Counters, gauges, and histograms |