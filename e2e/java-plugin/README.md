# Java observability plugin examples

Two runnable programs for the LaunchDarkly Java server observability plugin.
Both build against the plugin sources in this repository by default, so they
never need a published artifact.

## Self-check (`make run`)

`PluginExample` starts a local OTLP and sampling endpoint, registers
`ObservabilityPlugin` on an offline Java server SDK client, and exits non-zero
unless the collector saw the expected signals. A passing run prints one
`RESULT` line. `checkout-enabled` is the flag-evaluation span from
`TracingHook`. `kept-checkout` is kept by the sampling rule and carries
`launchdarkly.sampling.ratio`. `health-check` is dropped. A call to
`LDObserve` before the client starts must not install the OpenTelemetry no-op
global; the flag span only arrives when the hook is on the plugin's exporters.

```bash
make run
```

## HTTP server (`make serve`)

`ServerExample` is a plain `com.sun.net.httpserver` service on port 8080 with
`/`, `/rolldice` and `/error`. Each request is a span, evaluates a flag (which
the plugin records on the span), records a histogram, a counter and a log; the
`/error` route records an exception.

```bash
export LAUNCHDARKLY_SDK_KEY="key-from-your-project"   # unset = offline client
make serve &
until curl -s -o /dev/null localhost:8080/; do sleep 1; done
curl -s localhost:8080/rolldice
curl -s localhost:8080/error
```

Environment the server honours:

| Variable | Purpose |
|----------|---------|
| `LAUNCHDARKLY_SDK_KEY` | SDK key. Unset runs the client offline. |
| `PORT` | Listen port, default `8080`. |
| `OTEL_EXPORTER_OTLP_ENDPOINT` | OTLP/HTTP base URL, e.g. `http://localhost:4318` for a local stack. |
| `LD_OBSERVABILITY_BACKEND_URL` | Sampling-config URL for a local stack. |
| `HTTPS_PROXY` | `host:port` of an egress proxy; applied to exports and the sampling request. |

### Docker

The image is built from the repository root so the plugin can be compiled from
source. It is also wired into `e2e/compose.yml` as the `java` service, which
the dockerized e2e workflow starts and health-checks.

```bash
docker build -f e2e/java-plugin/Dockerfile -t e2e-java-plugin .
docker run --rm -p 8080:8080 -e LAUNCHDARKLY_SDK_KEY e2e-java-plugin
```

## Consumer check (`make consumer-check`)

Publishes the plugin to `~/.m2` and builds the example against that artifact
with `-PuseMavenLocal`, the way an application would. This verifies the
published POM: the example compiles against `Attributes` and `Severity` without
declaring `opentelemetry-api` itself, and no OkHttp, Okio or Kotlin arrives
transitively. CI runs this on every change to the plugin.
