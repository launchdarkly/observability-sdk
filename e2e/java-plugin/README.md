# Java observability plugin self-check

`PluginExample` is a runnable check for the LaunchDarkly Java server
observability plugin. It starts a local OTLP and sampling endpoint, registers
`ObservabilityPlugin` on an offline Java server SDK client, and exits non-zero
unless the collector saw the expected signals. It builds against the plugin
sources in this repository by default, so it never needs a published artifact.

For a web backend that uses the plugin the way a service does, see
[`../java-plugin-spring-boot`](../java-plugin-spring-boot).

## Run

```bash
make run
```

`make run` runs the check three times, once per owner of
`GlobalOpenTelemetry`: the plugin (`run`), a no-op claimed by
`GlobalOpenTelemetry.get()` before the client starts (`runClaimedGlobal`), and
the OpenTelemetry Java agent (`runWithJavaAgent`). The flag evaluation must
reach the plugin's exporter in all three.

Each passing run prints one `RESULT` line. `checkout-enabled` is the
flag-evaluation span from the plugin's tracing hook. `kept-checkout` is kept by the
sampling rule and carries `launchdarkly.sampling.ratio`. `health-check` is
dropped. A call to `LDObserve` before the client starts must not install the
OpenTelemetry no-op global; the flag span only arrives when the hook is on the
plugin's exporters.

## Consumer check

```bash
make consumer-check
```

Publishes the plugin to `~/.m2` and builds the example against that artifact
with `-PuseMavenLocal`, the way an application would. This verifies the
published POM: the example compiles against `Attributes` and `Severity` without
declaring `opentelemetry-api` itself, and no OkHttp, Okio or Kotlin arrives
transitively. CI runs this on JDK 11 and 21 for every change to the plugin.
