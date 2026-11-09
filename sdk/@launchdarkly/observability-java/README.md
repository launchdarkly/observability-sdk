# LaunchDarkly Observability plugin for the Java Server SDK

Official LaunchDarkly Observability plugin for the [Java Server SDK](https://github.com/launchdarkly/java-core). This plugin integrates OpenTelemetry tracing, logging, and metrics with LaunchDarkly feature flag evaluations.

> **Early Access** — This plugin is in early access preview. APIs may change before 1.0.

## Requirements

- Java 11+
- LaunchDarkly Java Server SDK 7.10.1+ (the first release with the plugin API). The plugin does not bring the SDK with it; declare it yourself. Older SDKs, such as the 7.4.0 that `launchdarkly-java-server-sdk-otel` 0.2.0 depends on, have no `Plugin` class and fail with `NoClassDefFoundError`.
- OpenTelemetry Java SDK 1.38.0+ on the runtime classpath. The plugin is built against 1.51.0, but a BOM in your build (Spring Boot's, for one) can change that version; see [OpenTelemetry versions](#opentelemetry-versions).

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
<dependency>
    <groupId>com.launchdarkly</groupId>
    <artifactId>launchdarkly-java-server-sdk</artifactId>
    <version>7.12.0</version>
</dependency>
```

The plugin does not bring the LaunchDarkly SDK with it, so you choose its version; declare both.

### Dependencies you get

- `io.opentelemetry:opentelemetry-api` is a compile-scoped (`api`) dependency, because `LDObserve` and `ObservabilityOptions` take OpenTelemetry API types such as `Attributes` and `Severity`. You do not need to declare it yourself. `io.opentelemetry:opentelemetry-sdk` is compile-scoped too, because `ObservabilityOptions` accepts SDK `SpanProcessor` and `LogRecordProcessor` instances.
- OTLP is sent over HTTP with OpenTelemetry's JDK sender, which uses `java.net.http.HttpClient`. The plugin does **not** pull OkHttp, Okio or the Kotlin standard library onto your classpath. The build fails if any of those leak in.
- The OpenTelemetry exporters are runtime dependencies, and together with the OpenTelemetry API and SDK they are the only ones: JSON is handled with the LaunchDarkly SDK's own `LDValue`, so no Gson or other JSON library is added. The plugin records flag evaluations with its own hook and does not depend on `launchdarkly-java-server-sdk-otel` or on the LaunchDarkly SDK itself. The build fails if the POM names anything outside `io.opentelemetry`.

### OpenTelemetry versions

The plugin needs OpenTelemetry Java 1.38.0 or newer at runtime. Dependency-management BOMs override the version the plugin asks for, so check what your build resolves (`./gradlew dependencies --configuration runtimeClasspath` or `mvn dependency:tree -Dincludes=io.opentelemetry`):

| Spring Boot | OpenTelemetry it manages | Plugin |
|-------------|--------------------------|--------|
| 3.2 | 1.31.0 | Too old: the plugin logs an error and stays off. Override the version. |
| 3.3 | 1.38.0 | Works |
| 3.4 | 1.43.0 | Works |
| 3.5 | 1.49.0 | Works |
| 4.1 | 1.62.0 | Works |

To override Spring Boot's version, set its `opentelemetry.version` property:

```groovy
// build.gradle, with io.spring.dependency-management
ext['opentelemetry.version'] = '1.51.0'
```

```xml
<!-- pom.xml, with spring-boot-starter-parent -->
<properties>
    <opentelemetry.version>1.51.0</opentelemetry.version>
</properties>
```

Keep every `io.opentelemetry` artifact on one version. If you add OpenTelemetry instrumentation libraries (such as the Logback appender below), pick the release built on the same core version (instrumentation 2.17.x is built on 1.51.0) or override `opentelemetry.version` to match it. Mixing them fails at runtime with `AbstractMethodError` or `NoSuchMethodError`.

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

Flag-evaluation spans are children of whatever span is current when you evaluate. The plugin does not create request spans itself: in a web service, connect your HTTP instrumentation to the plugin (see [Spring Boot](#spring-boot)) or each evaluation becomes its own trace.

On startup the plugin logs one line saying where it exports and under which service name, and warns if no service name is set. If it cannot start (a malformed endpoint, an OpenTelemetry version that is too old) it logs the cause as an error and stays a no-op; the LaunchDarkly client and your application keep running.

### Running alongside other OpenTelemetry setups

The plugin registers its SDK as `GlobalOpenTelemetry` when it can. When the global is already taken (the OpenTelemetry Java agent, your own SDK, a library that called `GlobalOpenTelemetry.get()` first, or `manualStart(true)` after such a call), the plugin keeps its own providers. Evaluation spans and everything sent through `LDObserve` still go to LaunchDarkly. The `feature_flag` event is added to whichever span is current, so it is exported by whoever owns that span.

`LDObserve.getOpenTelemetry()` returns the plugin's OpenTelemetry whether or not it is the global, for instrumentation that should export to LaunchDarkly through the plugin.

### Lifecycle and multiple clients

The OpenTelemetry providers are process-wide. The first `LDClient` that registers the plugin starts them and its SDK key identifies the project for everything exported. Another `LDClient` with the same SDK key shares them. An `LDClient` with a different SDK key logs a warning and its plugin starts nothing: its flag evaluations are still recorded, under the first client's SDK key.

Closing an `LDClient` does not stop the providers. Call `LDObserve.shutdown()` when your application stops (for example after closing the client) to flush and stop them; the plugin also registers one JVM shutdown hook that does the same, unless you turn it off with `shutdownHook(false)` (see [Spring Boot](#spring-boot)).

### OpenTelemetry Java agent

With the agent attached, the agent owns the global and creates the request spans, and exports them to its own endpoint. The plugin's evaluation spans point at those request spans, so for whole traces in LaunchDarkly send the agent's data there too:

```bash
export OTEL_EXPORTER_OTLP_ENDPOINT=https://otel.observability.app.launchdarkly.com:4318
export OTEL_EXPORTER_OTLP_PROTOCOL=http/protobuf
export OTEL_SERVICE_NAME=my-service
export OTEL_RESOURCE_ATTRIBUTES=highlight.project_id=YOUR_SDK_KEY
java -javaagent:opentelemetry-javaagent.jar -jar app.jar
```

`highlight.project_id` is the resource attribute the plugin itself sets to the SDK key. The agent also captures Logback and Log4j records and exports them the same way.

## Spring Boot

Build one `LDClient` bean with the plugin and let Spring close it. Spring Boot shuts down from its own JVM hook, after draining in-flight requests, so switch off the plugin's hook and stop the plugin from a bean instead; otherwise telemetry from requests that finish during the drain is lost.

```java
@Configuration
public class LaunchDarklyConfig {
    @Bean(destroyMethod = "close")
    public LDClient ldClient(@Value("${launchdarkly.sdk-key}") String sdkKey) {
        ObservabilityOptions options = new ObservabilityOptions.Builder()
                .serviceName("my-service")
                .serviceVersion("1.0.0")
                .environment("production")
                .shutdownHook(false)
                .build();
        LDConfig config = new LDConfig.Builder()
                .plugins(Components.plugins().setPlugins(List.of(new ObservabilityPlugin(options))))
                .build();
        return new LDClient(sdkKey, config);
    }

    /** Flushes and stops the plugin when the context closes. */
    @Bean(destroyMethod = "shutdown")
    public ObservabilityShutdown observabilityShutdown() {
        return new ObservabilityShutdown();
    }

    public static class ObservabilityShutdown {
        public void shutdown() {
            LDObserve.shutdown();
        }
    }
}
```

### Request spans and errors

Spring Boot's Micrometer tracing creates a span per HTTP request and records unhandled exceptions on it. Add the OpenTelemetry bridge and hand it the plugin's OpenTelemetry; Boot then exports request spans to LaunchDarkly with the flag-evaluation spans as children, the `feature_flag` events on the request span, and exceptions as error events. The `LDClient` parameter makes Spring start the plugin first.

```groovy
implementation 'org.springframework.boot:spring-boot-starter-actuator'
implementation 'io.micrometer:micrometer-tracing-bridge-otel'
```

```java
@Bean
public OpenTelemetry openTelemetry(LDClient ldClient) {
    return LDObserve.getOpenTelemetry();
}
```

The plugin's tracer provider records every span, so `management.tracing.sampling.probability` does not apply; the LaunchDarkly sampling rules decide what is kept.

### Logs

`LDObserve.recordLog` exports a record directly. To export what you already log through SLF4J and Logback, add the OpenTelemetry Logback appender (see [OpenTelemetry versions](#opentelemetry-versions) for the version) and install it on the plugin's OpenTelemetry:

```groovy
implementation 'io.opentelemetry.instrumentation:opentelemetry-logback-appender-1.0:2.17.0-alpha'
```

```xml
<!-- src/main/resources/logback-spring.xml -->
<configuration>
  <include resource="org/springframework/boot/logging/logback/base.xml"/>
  <appender name="OTEL" class="io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender"/>
  <root level="INFO">
    <appender-ref ref="CONSOLE"/>
    <appender-ref ref="OTEL"/>
  </root>
</configuration>
```

```java
@Bean
public OpenTelemetry openTelemetry(LDClient ldClient) {
    OpenTelemetry openTelemetry = LDObserve.getOpenTelemetry();
    OpenTelemetryAppender.install(openTelemetry);
    return openTelemetry;
}
```

Records logged inside a request carry its trace id. `logsApiLevel` still applies.

With Log4j 2 (`spring-boot-starter-log4j2`) use `io.opentelemetry.instrumentation:opentelemetry-log4j-appender-2.17` instead: add `<OpenTelemetry name="OpenTelemetryAppender"/>` to the appenders in `log4j2-spring.xml` (with `packages="io.opentelemetry.instrumentation.log4j.appender.v2_17"` on `<Configuration>`), reference it from the root logger, and call `io.opentelemetry.instrumentation.log4j.appender.v2_17.OpenTelemetryAppender.install(openTelemetry)` in the bean.

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

// Record errors: added to the current span if the plugin started it,
// otherwise recorded on a new error span
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

### Global attributes

`LDObserve.setAttributes` adds attributes to everything recorded after the call: every span and log record the plugin exports (flag-evaluation spans, error spans, and spans or records from instrumentation wired to `LDObserve.getOpenTelemetry()` included) and every metric recorded through `LDObserve`. Calling it again merges by key, and `LDObserve.removeAttributes` drops keys.

```java
import com.launchdarkly.observability.LDObserve;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;

import java.util.List;

LDObserve.setAttributes(Attributes.of(
    AttributeKey.stringKey("tenant.id"), "acme",
    AttributeKey.stringKey("deployment.region"), "us-east-1"));

// Later: replace one value and drop another
LDObserve.setAttributes(Attributes.of(AttributeKey.stringKey("deployment.region"), "eu-west-1"));
LDObserve.removeAttributes(List.of("tenant.id"));
```

- Attributes passed to an `LDObserve` call, or set on a span builder, win over global attributes of the same key; global attributes only fill keys that are missing. Resource attributes are separate and unchanged.
- A span gets the global attributes current when it starts.
- Keys are matched by name, whatever the attribute type.
- The attributes are held in memory for the life of the process (they survive `LDObserve.shutdown()`), are shared by every thread, and can be set before the plugin starts.
- Metrics recorded directly on a meter from `LDObserve.getOpenTelemetry()` do not get them; record through `LDObserve` instead.

### Custom span and log processors

`spanProcessors` and `logProcessors` add your own OpenTelemetry `SpanProcessor` and `LogRecordProcessor` instances to the plugin's pipeline, for example to enrich or redact telemetry before it is exported. `spanProcessor` and `logProcessor` add one at a time.

```java
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.logs.LogRecordProcessor;
import io.opentelemetry.sdk.trace.ReadWriteSpan;
import io.opentelemetry.sdk.trace.ReadableSpan;
import io.opentelemetry.sdk.trace.SpanProcessor;

AttributeKey<String> email = AttributeKey.stringKey("user.email");

SpanProcessor addPool = new SpanProcessor() {
    @Override
    public void onStart(Context parentContext, ReadWriteSpan span) {
        span.setAttribute("host.pool", "blue");
    }
    @Override public boolean isStartRequired() { return true; }
    @Override public void onEnd(ReadableSpan span) {}
    @Override public boolean isEndRequired() { return false; }
};
LogRecordProcessor redactEmail = (context, logRecord) -> {
    if (logRecord.toLogRecordData().getAttributes().get(email) != null) {
        logRecord.setAttribute(email, "[redacted]");
    }
};

ObservabilityOptions options = new ObservabilityOptions.Builder()
    .serviceName("my-service")
    .spanProcessors(List.of(addPool))
    .logProcessors(List.of(redactEmail))
    .build();
```

- Processors run in list order, after the plugin adds the global attributes and before its own processing: the `logsApiLevel` filter, the LaunchDarkly sampling rules and export. Changes a processor makes (in `onStart` for spans) are exported, and sampling still applies.
- A processor that throws is logged and skipped for that call; the span or record is still exported. `forceFlush` and `shutdown` are forwarded when the plugin flushes or shuts down.
- They are read when the providers start. A second `LDClient` with another SDK key does not add its processors (see [Lifecycle and multiple clients](#lifecycle-and-multiple-clients)).
- The interfaces come from the OpenTelemetry SDK (`opentelemetry-sdk`), which the plugin exposes as a compile-scoped dependency for this reason.

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
| `spanProcessors` / `spanProcessor` | none | Your own span processors, run in order before sampling and export. See [Custom span and log processors](#custom-span-and-log-processors) |
| `logProcessors` / `logProcessor` | none | Your own log record processors, run in order before the level filter, sampling and export |
| `shutdownHook` | `true` | Flush and stop the providers from a JVM shutdown hook. Set `false` under Spring Boot or another framework that owns shutdown, and call `LDObserve.shutdown()` from its close callback, so telemetry from requests drained during graceful shutdown is not dropped |

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

`:lib:build` fails if OkHttp, Okio or Kotlin appear on the runtime classpath, if the generated POM does not expose `opentelemetry-api` and `opentelemetry-sdk` at compile scope, or if it names anything outside `io.opentelemetry`. `./gradlew :lib:test -PldServerSdkVersion=7.10.1` runs the tests against the oldest supported SDK.

The unit tests run the sampling scenarios shared with the Go, .NET, Python and Node SDKs (`lib/src/test/resources/sampling`, copied from `go/internal/otel`). `:lib:build` also fails if those copies drift from Go's; copy the files over when the scenarios change.

## License

Apache-2.0
