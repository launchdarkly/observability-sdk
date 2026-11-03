# Spring Boot web backend with the Java observability plugin

A Spring Boot 3 service that uses the LaunchDarkly Java Server SDK with the
observability plugin the way a production backend does. It is the e2e app for
the plugin: CI builds it, runs it, and drives its routes, and it is the place
to exercise features the plugin needs on a server (request spans, flag
evaluations under load, errors, proxies, custom headers).

It builds against the plugin sources in this repository by default, so it
never needs a published artifact.

## Routes

| Route | What it exercises |
|-------|-------------------|
| `GET /` | Flag evaluation (`example-greeting`) inside the request span |
| `GET /rolldice` | Flag evaluation (`dice-sides`), a span attribute, JSON response |
| `GET /flags/{key}` | Any flag's JSON value for the request context |
| `GET /error` | An unhandled exception recorded with `LDObserve.recordError`, answered 500 |
| `GET /actuator/health` | Spring Boot health |

Send `X-User-Key: <key>` to pin the LaunchDarkly context; otherwise a random
user key is used per request. Every request gets a span, a duration histogram,
a request counter and a log line from `RequestTelemetryFilter`.

## Run

```bash
export LAUNCHDARKLY_SDK_KEY="key-from-your-project"   # unset = offline client
make run &
until curl -s -o /dev/null localhost:8080/; do sleep 1; done
curl -s localhost:8080/rolldice
curl -s localhost:8080/error
```

Environment the app honours:

| Variable | Purpose |
|----------|---------|
| `LAUNCHDARKLY_SDK_KEY` | SDK key. Unset runs the client offline. |
| `PORT` | Listen port, default `8080`. |
| `OTEL_EXPORTER_OTLP_ENDPOINT` | OTLP/HTTP base URL, e.g. `http://localhost:4318` for a local stack. |
| `LD_OBSERVABILITY_BACKEND_URL` | Sampling-config URL for a local stack. |
| `HTTPS_PROXY` | `host:port` of an egress proxy; applied to exports and the sampling request. |
| `SERVICE_VERSION` | Reported as `service.version`, default `dev`. |
| `LD_BASE_URI`, `LD_STREAM_URI`, `LD_EVENTS_URI` | Flag SDK polling, streaming and events URLs for a non-default LaunchDarkly instance. Used only when all three are set. |

### Global attributes repro

`Application.main` calls `GlobalAttributesRepro.applyGlobalAttributes()`
before Spring starts, so `LDObserve.setAttributes` runs before the
`LDClient` and the plugin exist. It sets `utm_campaign=spring_promo` and
`utm_source=email` on all telemetry. For every request, `RequestTelemetryFilter`
prints what to look for in LaunchDarkly:

```
[#1] expect utm_campaign=spring_promo on this row (span GET /rolldice)
[#1] expect utm_campaign=spring_promo on this row (metric http.server.requests, route /rolldice)
[#1] expect utm_campaign=spring_promo on this row (log "GET /rolldice -> 200")
[#1] expect utm_source=per_call (per-call wins over global) (log "GET /rolldice -> 200")
```

The request log line passes `utm_source=per_call` itself, so it must show
`per_call` rather than the global `email`. Flag-evaluation spans and errors
from `/error` carry the globals too. The tests boot the context without
`main`, so they run without the globals.

`make build` produces the executable jar in `build/libs/`. `make test` boots
the application against a local OTLP collector and sampling endpoint
(`ApplicationTelemetryTest`), drives every route, and checks the exported
request spans, evaluation spans and `feature_flag` events, the recorded error,
logs, metrics, resource attributes and a backend sampling rule. It also runs a
guard that fails if the plugin leaks OkHttp, Okio or Kotlin onto the
application's classpath.

## Under the OpenTelemetry Java agent

```bash
make javaagent-smoke
```

Runs the jar with the OpenTelemetry Java agent attached, so the agent owns
`GlobalOpenTelemetry`, and drives the routes against a local collector. It
fails unless the evaluation spans and the request spans with their
`feature_flag` events reach the plugin's exporter. CI runs it on every change
to the plugin.

## Docker

The image is built from the repository root so the plugin can be compiled
from source. It is the `java-plugin-spring-boot` service in `e2e/compose.yml`, which the
dockerized e2e workflow builds, starts and health-checks.

```bash
docker build -f e2e/java-plugin-spring-boot/Dockerfile -t e2e-java .
docker run --rm -p 8080:8080 -e LAUNCHDARKLY_SDK_KEY e2e-java
```

## Consumer check

```bash
make consumer-check
```

Publishes the plugin to `~/.m2` and builds the app against that artifact with
`-PuseMavenLocal`, the way an application would. The app compiles against
`Attributes`, `Severity` and `Span` without declaring `opentelemetry-api`
itself, which checks the published POM's scopes.
