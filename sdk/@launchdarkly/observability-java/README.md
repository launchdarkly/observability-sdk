# LaunchDarkly Observability Java SDK

Official LaunchDarkly Observability plugin for the [Java Server SDK](https://github.com/launchdarkly/java-core). This plugin integrates OpenTelemetry tracing, logging, and metrics with LaunchDarkly feature flag evaluations.

> **Early Access** — This plugin is in early access preview. APIs may change before 1.0.

## Requirements

- Java 11+
- LaunchDarkly Java Server SDK 7.12.0+

## Installation

### Gradle

```kotlin
dependencies {
    implementation("com.launchdarkly:launchdarkly-observability-java:0.1.0")
    implementation("com.launchdarkly:launchdarkly-java-server-sdk:7.12.0")
}
```

### Maven

```xml
<dependency>
    <groupId>com.launchdarkly</groupId>
    <artifactId>launchdarkly-observability-java</artifactId>
    <version>0.1.0</version>
</dependency>
```

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
2. Registers a `TracingHook` that adds span events for each flag evaluation, enabling guarded rollouts and observability.
3. Fetches sampling configuration from the backend to control telemetry volume.

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

## Configuration Options

| Option | Default | Description |
|--------|---------|-------------|
| `serviceName` | `""` | Service name for resource attributes |
| `serviceVersion` | `""` | Service version for resource attributes |
| `environment` | `""` | Deployment environment name |
| `otlpEndpoint` | LaunchDarkly endpoint | Custom OTLP endpoint |
| `backendUrl` | LaunchDarkly backend | Custom backend URL for sampling config |
| `debug` | `false` | Enable debug logging |
| `manualStart` | `false` | Delay OTLP provider startup |

## License

Apache-2.0
