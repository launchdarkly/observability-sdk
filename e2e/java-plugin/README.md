# Java observability plugin example

Runnable check for the LaunchDarkly Java server observability plugin. It starts a local OTLP and sampling endpoint, registers `ObservabilityPlugin` on an offline Java server SDK client, and exits non-zero unless the collector saw the expected spans.

## Run

From this directory:

```bash
make run
```

That uses the Java SDK's Gradle wrapper and the local `observability-java` project, so the example does not need a published artifact.

A passing run prints one `RESULT` line. `checkout-enabled` is the flag-evaluation span from `TracingHook`. `kept-checkout` is kept by the sampling rule and carries `launchdarkly.sampling.ratio`. `health-check` is dropped. A call to `LDObserve` before the client starts must not install the OpenTelemetry no-op global; the flag span only arrives when the hook is on the plugin's exporters.
