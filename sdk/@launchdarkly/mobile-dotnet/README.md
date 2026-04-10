# LaunchDarkly Observability SDK for .NET MAUI

The LaunchDarkly Observability SDK for .NET MAUI provides automatic and manual instrumentation for your mobile application, including metrics, logs, error reporting, and session replay.

## Early Access Preview

**NB: APIs are subject to change until a 1.x version is released.**

## Features

### Automatic Instrumentation

The .NET MAUI observability plugin automatically instruments:
- **HTTP Requests**: Outgoing HTTP requests
- **Crash Reporting**: Automatic crash reporting and stack traces
- **Feature Flag Evaluations**: Evaluation events added to your spans
- **Session Management**: User session tracking and background timeout handling

## Prerequisites

*   **.NET 9.0** or higher is required.
*   MAUI support for **iOS** and **Android**.

## Example Application

A complete example application is available in the [sample](./sample) directory.

To **profile Android build time** (binary log / task hotspots), see [docs/build-profiling.md](./docs/build-profiling.md) and [`scripts/profile_android_build.sh`](./scripts/profile_android_build.sh).

## Usage

### Basic Setup

In your `MauiProgram.cs` (or wherever you initialize your application), register the `ObservabilityPlugin` via `LdClient`:

```csharp
using LaunchDarkly.Observability;
using LaunchDarkly.Sdk;
using LaunchDarkly.Sdk.Client;
using LaunchDarkly.Sdk.Client.Integrations;

public static class MauiProgram
{
    public static MauiApp CreateMauiApp()
    {
        var builder = MauiApp.CreateBuilder();

        // ... other configuration ...

        var mobileKey = "your-mobile-key";

        var ldConfig = Configuration.Builder(mobileKey, ConfigurationBuilder.AutoEnvAttributes.Enabled)
            .Plugins(new PluginConfigurationBuilder()
                .Add(new ObservabilityPlugin(new ObservabilityOptions(
                    isEnabled: true,
                    serviceName: "maui-sample-app"
                )))
            ).Build();

        var context = Context.New("maui-user-key");
        var client = LdClient.Init(ldConfig, context, TimeSpan.FromSeconds(10));

        return builder.Build();
    }
}
```

### Recording Observability Data

After initialization of the LaunchDarkly client, use `LDObserve` to record metrics, logs, and errors:

```csharp
using LaunchDarkly.Observability;

// Record metrics
LDObserve.RecordMetric("user_actions", 1.0);
LDObserve.RecordCount("api_calls", 1.0);
LDObserve.RecordIncr("page_views", 1.0);
LDObserve.RecordHistogram("response_time", 150.0);
LDObserve.RecordUpDownCounter("active_connections", 1.0);

// Record logs with severity and optional attributes
LDObserve.RecordLog(
    "User performed action",
    Severity.Info,
    new Dictionary<string, object?>
    {
        { "user_id", "12345" },
        { "action", "button_click" }
    }
);

// Record errors from an exception
var exception = new Exception("Something went wrong", new InvalidOperationException("root cause"));
LDObserve.RecordError(exception);

// Or record errors from a message string
LDObserve.RecordError("Something went wrong", "The underlying cause of the error.");
```

#### Metrics

| Method | Description |
|---|---|
| `RecordMetric(name, value)` | Record a gauge metric |
| `RecordCount(name, value)` | Record a count metric |
| `RecordIncr(name, value)` | Record an incremental counter metric |
| `RecordHistogram(name, value)` | Record a histogram metric |
| `RecordUpDownCounter(name, value)` | Record an up-down counter metric |

#### Logs

Use `RecordLog` to emit structured log records with a severity level and optional attributes:

```csharp
LDObserve.RecordLog(
    "Checkout completed",
    Severity.Info,
    new Dictionary<string, object?>
    {
        { "order_id", "ORD-9876" },
        { "total", 42.99 }
    }
);
```

Supported severity levels: `Trace`, `Debug`, `Info`, `Warn`, `Error`, `Fatal`.

##### Logs with Span Context

You can correlate logs with a specific span for distributed tracing by capturing and passing a `SpanContext`:

```csharp
var span = LDObserve.StartActiveSpan("checkout-flow");
var capturedContext = span.Context;
span.End();

await Task.Run(() =>
{
    LDObserve.RecordLog(
        "Checkout processed on background thread",
        Severity.Warn,
        new Dictionary<string, object?> { { "source", "background-task" } },
        spanContext: capturedContext);
});
```

This is useful when a log is emitted on a different thread or after the span has ended, but you still want it associated with the original trace.

#### Errors

Use `RecordError` to capture error events. You can pass an `Exception` object directly, or a message string with an optional cause:

```csharp
// From an exception (inner exceptions are captured automatically)
var exception = new Exception("Payment failed", new TimeoutException("Connection timed out"));
LDObserve.RecordError(exception);

// From a message string with optional cause
LDObserve.RecordError("Payment failed", "Timeout connecting to payment gateway.");
```

#### Traces

> For in-depth examples covering nested spans, error handling, manual context propagation, and more, see the [Distributed Tracing Guide](./docs/distributed-tracing.md).

Use `LDObserve` to create spans for tracing operations in your application. Spans are backed by [OpenTelemetry](https://opentelemetry.io/) and should be disposed when the operation completes.

Create spans to trace operations. The returned `TelemetrySpan` is disposable — wrap it in a `using` statement so it ends automatically:

```csharp
using var span = LDObserve.StartActiveSpan("api_request");
span.SetAttribute("endpoint", "/api/users");
span.SetAttribute("method", "GET");
span.AddEvent("cache.miss");
```

##### Nested Spans

`StartActiveSpan` automatically creates parent-child relationships. Each new active span becomes a child of the currently active span:

```csharp
using var parent = LDObserve.StartActiveSpan("ProcessOrder");
using var child = LDObserve.StartActiveSpan("ValidatePayment");
using var grandchild = LDObserve.StartActiveSpan("ChargeCard");

await httpClient.PostAsync("https://api.example.com/charge", content);
```

##### Independent Root Spans

Use `StartRootSpan` to create spans that are independent of the current trace context. This is useful for sequential operations that should not be nested:

```csharp
using (var span1 = LDObserve.StartRootSpan("SequentialOperation1"))
{
    span1.SetAttribute("sequence", "1");
}

using (var span2 = LDObserve.StartRootSpan("SequentialOperation2"))
{
    span2.SetAttribute("sequence", "2");
}
```

##### Manual Span Lifecycle

You can also manage a span's lifecycle manually using `End()` and capture its `Context` for distributed tracing:

```csharp
var span = LDObserve.StartActiveSpan("my-operation");
span.SetAttribute("key", "value");
var capturedContext = span.Context;
span.End();
```

| Method | Description |
|---|---|
| `LDObserve.StartActiveSpan(name)` | Start a new active span that automatically nests under the current parent. Returns a disposable `TelemetrySpan`. |
| `LDObserve.StartActiveSpan(name, parentContext)` | Start a new active span with an explicit parent `SpanContext` (defaults to `SpanKind.Internal`). Use when `Activity.Current` is not propagated. |
| `LDObserve.StartActiveSpan(name, kind, parentContext)` | Same as above but with an explicit `SpanKind`. |
| `LDObserve.StartRootSpan(name)` | Start a new root span with no parent. Returns a disposable `TelemetrySpan`. |
| `span.SetAttribute(key, value)` | Set a key-value attribute on a span. |
| `span.AddEvent(name)` | Record a named event on a span. |
| `span.Context` | Capture the span's context for correlating logs or other operations across async boundaries. |
| `span.End()` | Manually end a span (alternatively, use a `using` statement). |

##### Using `System.Diagnostics.Activity`

If you prefer the native .NET `Activity` API over OpenTelemetry's `TelemetrySpan`, equivalent methods are available. Activities are nullable — `StartActivity` returns `null` before SDK initialization or when no listener is registered:

```csharp
using System.Diagnostics;

using var activity = LDObserve.StartActivity("api_request");
activity?.SetTag("endpoint", "/api/users");
activity?.SetTag("method", "GET");
activity?.AddEvent(new ActivityEvent("cache.miss"));

// Root activity (no parent), equivalent to StartRootSpan
using var root = LDObserve.StartRootActivity("independent-operation");
root?.SetTag("sequence", "1");

// Or get the ActivitySource directly for full control
var source = LDObserve.GetActivitySource();
using var custom = source.StartActivity("custom", ActivityKind.Client);
```

### Identifying Users

Use the LaunchDarkly client to identify or switch user contexts. This ties observability data to the correct user:

```csharp
using LaunchDarkly.Sdk;
using LaunchDarkly.Sdk.Client;

// Single context
var userContext = Context.Builder("user-key")
    .Name("Bob Bobberson")
    .Build();
await LdClient.Instance.IdentifyAsync(userContext);

// Multi-context
var userContext = Context.Builder("user-key")
    .Name("Bob Bobberson")
    .Build();
var deviceContext = Context.Builder(ContextKind.Of("device"), "iphone")
    .Name("iphone")
    .Build();
var multiContext = Context.MultiBuilder()
    .Add(userContext)
    .Add(deviceContext)
    .Build();
LdClient.Instance.Identify(multiContext, TimeSpan.FromSeconds(5));

// Anonymous context
var anonContext = Context.Builder("anonymous-key")
    .Anonymous(true)
    .Build();
LdClient.Instance.Identify(anonContext, TimeSpan.FromSeconds(5));
```

## Session Replay

Session Replay captures user interactions and screen recordings to help you understand how users interact with your application. To enable Session Replay, add the `SessionReplayPlugin` alongside the `ObservabilityPlugin`:

```csharp
using LaunchDarkly.SessionReplay;
using LaunchDarkly.Observability;
using LaunchDarkly.Sdk;
using LaunchDarkly.Sdk.Client;
using LaunchDarkly.Sdk.Client.Integrations;

public static class MauiProgram
{
    public static MauiApp CreateMauiApp()
    {
        var builder = MauiApp.CreateBuilder();

        // ... other configuration ...

        var mobileKey = "your-mobile-key";

        var ldConfig = Configuration.Builder(mobileKey, ConfigurationBuilder.AutoEnvAttributes.Enabled)
            .Plugins(new PluginConfigurationBuilder()
                .Add(new ObservabilityPlugin(new ObservabilityOptions(
                    isEnabled: true,
                    serviceName: "maui-sample-app"
                )))
                .Add(new SessionReplayPlugin(new SessionReplayOptions(
                    isEnabled: true,
                    privacy: new SessionReplayOptions.PrivacyOptions(
                        maskTextInputs: true,
                        maskWebViews: false,
                        maskLabels: false
                    )
                )))
            ).Build();

        var context = Context.New("maui-user-key");
        var client = LdClient.Init(ldConfig, context, TimeSpan.FromSeconds(10));

        return builder.Build();
    }
}
```

### Privacy Options

You can control what information is captured during a session using `PrivacyOptions`:

*   `MaskTextInputs`: (Default: `true`) Masks all text input fields.
*   `MaskWebViews`: (Default: `false`) Masks all web view content.
*   `MaskLabels`: (Default: `false`) Masks all text labels.
*   `MaskImages`: (Default: `false`) Masks all images.

### Manual Masking

You can manually mask or unmask specific UI components using the provided extension methods on any MAUI `View`.

```csharp
using LaunchDarkly.SessionReplay;

// Mask a specific view
mySensitiveView.LDMask();

// Unmask a specific view
myPublicView.LDUnmask();
```

## Troubleshooting

### Android CI builds (timeouts, low memory)

The SDK adds many Android AAR/JAR libraries for OpenTelemetry and the bridge. That increases **DEX / R8 / Java** work during the app build. On small CI VMs you may see slow builds or the process being killed when memory is tight.

**Mitigations:**

- **Give the build more RAM** on the agent (Android + MAUI + R8 is memory-heavy).
- **Raise the Java heap** used by the Android toolchain where your pipeline allows (for example `JavaMaximumHeapSize` in the app project, or Gradle `org.gradle.jvmargs` if you invoke Gradle directly).
- **Reduce MSBuild parallelism** on constrained machines (for example `dotnet build -m:1` or fewer parallel projects) to lower peak memory at the cost of wall time.
- **Measure before tuning:** capture a binary log and find the slow targets (see [docs/build-profiling.md](./docs/build-profiling.md)).

For how natives and NuGet dependencies are structured, see [docs/native-android-dependencies.md](./docs/native-android-dependencies.md) and [docs/nuget-package-dependencies.md](./docs/nuget-package-dependencies.md).

## Contributing

We encourage pull requests and other contributions from the community. Check out our [contributing guidelines](../../CONTRIBUTING.md) for instructions on how to contribute to this SDK.

## About LaunchDarkly

* LaunchDarkly is a continuous delivery platform that provides feature flags as a service and allows developers to iterate quickly and safely. We allow you to easily flag your features and manage them from the LaunchDarkly dashboard.  With LaunchDarkly, you can:
    * Roll out a new feature to a subset of your users (like a group of users who opt-in to a beta tester group), gathering feedback and bug reports from real-world use cases.
    * Gradually roll out a feature to an increasing percentage of users, and track the effect that the feature has on key metrics (for instance, how likely is a user to complete a purchase if they have feature A versus feature B?).
    * Turn off a feature that you realize is causing performance problems in production, without needing to re-deploy, or even restart the application with a changed configuration file.
    * Grant access to certain features based on user attributes, like payment plan (eg: users on the 'gold' plan get access to more features than users in the 'silver' plan). Disable parts of your application to facilitate maintenance, without taking everything offline.
* LaunchDarkly provides feature flag SDKs for a wide variety of languages and technologies. Read [our documentation](https://docs.launchdarkly.com/sdk) for a complete list.
* Explore LaunchDarkly
    * [launchdarkly.com](https://www.launchdarkly.com/ "LaunchDarkly Main Website") for more information
    * [docs.launchdarkly.com](https://docs.launchdarkly.com/  "LaunchDarkly Documentation") for our documentation and SDK reference guides
    * [apidocs.launchdarkly.com](https://apidocs.launchdarkly.com/  "LaunchDarkly API Documentation") for our API documentation
    * [launchdarkly.com/blog](https://launchdarkly.com/blog/  "LaunchDarkly Blog Documentation") for the latest product updates
