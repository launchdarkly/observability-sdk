# AspSampleApp

A minimal ASP.NET Core (net8.0) app that demonstrates the
`LaunchDarkly.Observability` plugin. It exercises the public `Observe` APIs —
exception recording, metrics, logs, manual spans — alongside a LaunchDarkly
feature flag evaluation, so you can verify telemetry flows end-to-end.

The project references the local plugin source
(`../src/LaunchDarkly.Observability`) rather than the published NuGet package,
making it useful for plugin development as well.

## Prerequisites

- .NET 8 SDK
- A LaunchDarkly server-side SDK key

## Running

From the `AspSampleApp/` directory:

```shell
export LAUNCHDARKLY_SDK_KEY="<your-server-sdk-key>"
dotnet run
```

The app listens on the ports configured by the ASP.NET Core launch profile
(HTTPS redirect is enabled). In `Development`, Swagger UI is exposed at
`/swagger`.

If you want the `/weatherforecast` and `/manualinstrumentation` endpoints to
exhibit non-default behavior, create boolean flags named `isMercury` and
`enableMetrics` in your LaunchDarkly project.

### Configuring the service

The plugin is configured in `Program.cs`:

```csharp
var config = Configuration.Builder(Environment.GetEnvironmentVariable("LAUNCHDARKLY_SDK_KEY"))
    .Plugins(new PluginConfigurationBuilder()
        .Add(ObservabilityPlugin.Builder(builder.Services)
            .WithServiceName("asp-core-test-service")
            .WithServiceVersion("0.0.0")
            .Build())).Build();
```

Change `WithServiceName` / `WithServiceVersion` to label the telemetry emitted
by your run.

## Endpoints

| Method | Path                      | What it demonstrates                                                                                 |
| ------ | ------------------------- | ---------------------------------------------------------------------------------------------------- |
| GET    | `/recordexception`        | `Observe.RecordException` with custom attributes.                                                    |
| GET    | `/recordmetrics`          | `RecordMetric` (gauge), `RecordCount`, `RecordIncr`, `RecordHistogram`, and `RecordUpDownCounter`.   |
| GET    | `/recordlog`              | `Observe.RecordLog` at varying severities with structured attributes.                                |
| GET    | `/manualinstrumentation`  | `Observe.StartActivity` to create a manual span; gated by the `enableMetrics` flag.                  |
| GET    | `/weatherforecast`        | Evaluates the `isMercury` flag and returns a forecast whose temperature range depends on the value. |
| GET    | `/crash`                  | Throws `NotImplementedException` to test unhandled-exception capture.                                |

Example:

```shell
curl http://localhost:5247/recordmetrics
curl http://localhost:5247/weatherforecast
```

A ready-to-run request is also in `AspSampleApp.http` for JetBrains / VS Code
HTTP clients.

## Verifying telemetry

Once the endpoints have been hit, traces, metrics, logs, and errors should
appear in the LaunchDarkly observability UI under the service name configured
above. The default backend is
`https://pub.observability.app.launchdarkly.com`; override it on the plugin
builder if you're pointing at a different environment.
