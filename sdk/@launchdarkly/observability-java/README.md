# LaunchDarkly Observability plugin for the Java Server SDK

Official LaunchDarkly Observability plugin for the [Java Server SDK](https://github.com/launchdarkly/java-core). This plugin integrates OpenTelemetry tracing, logging, and metrics with LaunchDarkly feature flag evaluations.

> **Early Access** — This plugin is in early access preview. APIs may change before 1.0.

## Requirements

- Java 11+
- LaunchDarkly Java Server SDK 7.12.0+

## Installation

The artifact follows the Java server SDK add-on family (`launchdarkly-java-server-sdk-otel`, `-redis-store`, `-ai`). The Android observability plugin is a separate artifact, `launchdarkly-observability-android`.

### Gradle

```groovy
dependencies {
    implementation 'com.launchdarkly:launchdarkly-java-server-sdk-observability:0.1.0'
    implementation 'com.launchdarkly:launchdarkly-java-server-sdk:7.12.0'
}
```

### Maven

```xml
<dependency>
    <groupId>com.launchdarkly</groupId>
    <artifactId>launchdarkly-java-server-sdk-observability</artifactId>
    <version>0.1.0</version>
</dependency>
```

### Dependencies you get

- `io.opentelemetry:opentelemetry-api` is a compile-scoped (`api`) dependency, because `LDObserve` and `ObservabilityOptions` take OpenTelemetry API types such as `Attributes` and `Severity`. You do not need to declare it yourself.
- OTLP is sent over HTTP with OpenTelemetry's JDK sender, which uses `java.net.http.HttpClient`. The plugin does **not** pull OkHttp, Okio or the Kotlin standard library onto your classpath. The build fails if any of those leak in.
- The OpenTelemetry SDK, the LaunchDarkly OTel tracing hook and Gson are runtime dependencies.

## Usage

```java
import com.launchdarkly.observability.ObservabilityPlugin;
import com.launchdarkly.observability.ObservabilityOptions;
import com.launchdarkly.sdk.server.Components;
import com.launchdarkly.sdk.server.LDClient;
import com.launchdarkly.sdk.server.LDConfig;

import java.util.List;

LDConfig config = new LDConfig.Builder()
    .plugins(Components.plugins().setPlugins(List.of(
        new ObservabilityPlugin(
            new ObservabilityOptions.Builder()
                .serviceName("my-service")
                .serviceVersion("1.0.0")
                .environment("production")
                .build()
        )
    )))
    .build();

LDClient client = new LDClient("YOUR_SDK_KEY", config);
```

The plugin automatically:

1. Sets up OpenTelemetry TracerProvider, LoggerProvider, and MeterProvider with OTLP HTTP exporters pointed at the LaunchDarkly observability backend.
2. Registers a tracing hook that records a span for each flag evaluation and adds a `feature_flag` event to the current span, enabling guarded rollouts and observability.
3. Fetches sampling configuration from the backend to control telemetry volume. Regex rules match anywhere in the value, as in the other SDKs; a rule whose pattern does not compile is skipped with a warning.

### Running alongside other OpenTelemetry setups

The plugin registers its SDK as `GlobalOpenTelemetry` when it can. When the global is already taken (the OpenTelemetry Java agent, your own SDK, a library that called `GlobalOpenTelemetry.get()` first, or `manualStart(true)` after such a call), the plugin keeps its own providers. Evaluation spans and everything sent through `LDObserve` still go to LaunchDarkly. The `feature_flag` event is added to whichever span is current, so it is exported by whoever owns that span.

## Manual Instrumentation

Use `LDObserve` for custom telemetry:

```java
import com.launchdarkly.observability.LDObserve;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.logs.Severity;
import io.opentelemetry.api.trace.Span;

// Record metrics
LDObserve.recordCount("requests.total", 1, Attributes.empty());
LDObserve.recordHistogram("request.duration_ms", 42.5, Attributes.empty());
LDObserve.recordMetric("queue.depth", 12.0, Attributes.empty());

// Record logs
LDObserve.recordLog("User logged in", Severity.INFO, Attributes.empty());

// Record errors
LDObserve.recordError(exception, Attributes.empty());

// Create spans
Span span = LDObserve.startSpan("my-operation", Attributes.empty());
try {
    // ... your code ...
} finally {
    span.end();
}

// Flush / shutdown
LDObserve.flush();
LDObserve.shutdown();
```

Every `LDObserve` method is safe to call before the plugin is initialized; such calls are local no-ops and do not install the OpenTelemetry no-op global.

## Configuration Options

| Option | Default | Description |
|--------|---------|-------------|
| `enabled` | `true` | When `false`, no providers start, no flag-evaluation hook is registered, no sampling config is fetched, and `LDObserve` is a no-op. |
| `serviceName` | `""` | Service name for resource attributes |
| `serviceVersion` | `""` | Service version for resource attributes |
| `environment` | `""` | Deployment environment name |
| `otlpEndpoint` | LaunchDarkly endpoint | Custom OTLP/HTTP endpoint |
| `backendUrl` | LaunchDarkly backend | Custom backend URL for sampling config |
| `resourceAttributes` | empty | Extra resource attributes merged over the plugin's defaults, so they can override `service.name` and friends |
| `customHeaders` / `customHeader` | none | Headers sent with every OTLP export and the sampling-config request |
| `proxySelector` / `proxyHostAndPort` | JVM default | Egress proxy for exports and the sampling-config request |
| `logsApiLevel` | `INFO` | Minimum severity for exported logs. `NONE` disables log export. |
| `debug` | `false` | Enable debug logging |
| `manualStart` | `false` | Delay OTLP provider startup until `LDObserve.start(sdkKey, options)` |

### Proxies and custom headers

Both the OTLP exporters and the sampling-config request use `java.net.http.HttpClient`, so they behave the same way behind an egress proxy. With nothing configured, the JVM default proxy selector applies, which honours `-Dhttps.proxyHost`, `-Dhttps.proxyPort` and `-Dhttp.nonProxyHosts`. To set a proxy or headers explicitly:

```java
new ObservabilityOptions.Builder()
    .proxyHostAndPort("egress.internal", 3128)
    .customHeader("X-Tenant", "acme")
    .build();
```

`proxySelector(java.net.ProxySelector)` accepts any selector when per-host rules are needed.

### Log level

```java
new ObservabilityOptions.Builder()
    .logsApiLevel(ObservabilityOptions.LogLevel.WARN)   // export WARN, ERROR and FATAL
    .build();
```

Levels are `TRACE`, `DEBUG`, `INFO`, `WARN`, `ERROR`, `FATAL` and `NONE`, matching the OpenTelemetry severity numbers. A record whose severity is unset is treated as `TRACE`.

## Development

```bash
./gradlew :lib:build                 # compile, unit tests, classpath and POM guards
./gradlew :lib:publishToMavenLocal   # then see ../../e2e/java-plugin for consumer and server examples
```

`:lib:build` fails if OkHttp, Okio or Kotlin appear on the runtime classpath, or if the generated POM does not expose `opentelemetry-api` at compile scope.

## License

Apache-2.0
