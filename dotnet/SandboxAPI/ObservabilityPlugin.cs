using LaunchDarkly.Sdk.Integrations.Plugins;
using LaunchDarkly.Sdk.Server.Interfaces;
using LaunchDarkly.Sdk.Server.Plugins;
using OpenTelemetry.Exporter;
using OpenTelemetry.Logs;
using OpenTelemetry.Metrics;
using OpenTelemetry.Resources;
using OpenTelemetry.Trace;
using System.Diagnostics;

public class ObservabilityPlugin : Plugin
{
    private const string ServiceName = "sandbox-api";
    public static ActivitySource ActivitySource { get; } = new ActivitySource(ServiceName);

    public ObservabilityPlugin(string name = "observability-plugin")
        : base(name) { }

    public override void Register(ILdClient client, EnvironmentMetadata metadata)
    {
        // OpenTelemetry setup is handled via the static configuration methods
    }
}

/// <summary>
/// Static class containing extension methods for configuring observability
/// </summary>
public static class ObservabilityExtensions
{
    private const string ServiceName = "sandbox-api";

    /// <summary>
    /// Extension method to configure OpenTelemetry for the web application builder.
    /// This encapsulates all OpenTelemetry setup including logging, tracing, and metrics.
    /// </summary>
    /// <param name="builder">The web application builder</param>
    /// <returns>The web application builder for method chaining</returns>
    public static WebApplicationBuilder AddObservability(this WebApplicationBuilder builder)
    {
        // Configure OpenTelemetry Logging
        builder.Logging.AddOpenTelemetry(options =>
        {
            options
                .SetResourceBuilder(
                    ResourceBuilder.CreateDefault()
                        .AddService(ServiceName))
                .AddConsoleExporter()
                .AddOtlpExporter(otlpOptions =>
                {
                    otlpOptions.Endpoint = new Uri("http://localhost:4318/v1/logs");
                    otlpOptions.Protocol = OtlpExportProtocol.HttpProtobuf;
                });
        });

        // Configure OpenTelemetry Tracing and Metrics
        builder.Services.AddOpenTelemetry()
            .ConfigureResource(resource => resource.AddService(ServiceName))
            .WithTracing(tracing => tracing
                .AddAspNetCoreInstrumentation()
                .AddSource(ServiceName)
                .AddConsoleExporter()
                .AddOtlpExporter(otlpOptions =>
                {
                    otlpOptions.Endpoint = new Uri("http://localhost:4318/v1/traces");
                    otlpOptions.Protocol = OtlpExportProtocol.HttpProtobuf;
                }))
            .WithMetrics(metrics => metrics
                .AddAspNetCoreInstrumentation()
                .AddConsoleExporter()
                .AddOtlpExporter(otlpOptions =>
                {
                    otlpOptions.Endpoint = new Uri("http://localhost:4318/v1/metrics");
                    otlpOptions.Protocol = OtlpExportProtocol.HttpProtobuf;
                }));

        return builder;
    }
}