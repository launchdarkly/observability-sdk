using LaunchDarkly.Sdk.Integrations.Plugins;
using LaunchDarkly.Sdk.Server.Interfaces;
using LaunchDarkly.Sdk.Server.Plugins;
using OpenTelemetry.Exporter;
using OpenTelemetry.Logs;
using OpenTelemetry.Metrics;
using OpenTelemetry.Resources;
using OpenTelemetry.Trace;

public class ObservabilityPluginConfig
{
    public required string ServiceName { get; set; }
    public required string OtlpEndpoint { get; set; }
    public required OtlpExportProtocol OtlpProtocol { get; set; }
}

public class ObservabilityPlugin : Plugin
{
    public ObservabilityPlugin(ObservabilityPluginConfig config) : base(config.ServiceName) {}

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
    /// <summary>
    /// Extension method to configure OpenTelemetry for the web application builder.
    /// This encapsulates all OpenTelemetry setup including logging, tracing, and metrics.
    /// </summary>
    /// <param name="builder">The web application builder</param>
    /// <param name="config">The observability configuration</param>
    /// <returns>The web application builder for method chaining</returns>
    public static WebApplicationBuilder AddObservability(this WebApplicationBuilder builder, ObservabilityPluginConfig config)
    {
        // Configure OpenTelemetry Logging
        builder.Logging.AddOpenTelemetry(options =>
        {
            options
                .SetResourceBuilder(
                    ResourceBuilder.CreateDefault()
                        .AddService(config.ServiceName))
                .AddConsoleExporter()
                .AddOtlpExporter(otlpOptions =>
                {
                    otlpOptions.Endpoint = new Uri(config.OtlpEndpoint + "/v1/logs");
                    otlpOptions.Protocol = config.OtlpProtocol;
                });
        });

        // Configure OpenTelemetry Tracing and Metrics
        builder.Services.AddOpenTelemetry()
            .ConfigureResource(resource => resource.AddService(config.ServiceName))
            .WithTracing(tracing => tracing
                .AddAspNetCoreInstrumentation()
                .AddSource(config.ServiceName)
                .AddConsoleExporter()
                .AddOtlpExporter(otlpOptions =>
                {
                    otlpOptions.Endpoint = new Uri(config.OtlpEndpoint + "/v1/traces");
                    otlpOptions.Protocol = config.OtlpProtocol;
                }))
            .WithMetrics(metrics => metrics
                .AddAspNetCoreInstrumentation()
                .AddConsoleExporter()
                .AddOtlpExporter(otlpOptions =>
                {
                    otlpOptions.Endpoint = new Uri(config.OtlpEndpoint + "/v1/metrics");
                    otlpOptions.Protocol = config.OtlpProtocol;
                }));

        return builder;
    }
}