using System.Diagnostics;
using LaunchDarkly.Sdk.Integrations.Plugins;
using LaunchDarkly.Sdk.Server.Interfaces;
using LaunchDarkly.Sdk.Server.Plugins;
using OpenTelemetry;
using OpenTelemetry.Exporter;
using OpenTelemetry.Logs;
using OpenTelemetry.Metrics;
using OpenTelemetry.Resources;
using OpenTelemetry.Trace;
using static ObservabilityPlugin;

public class ObservabilityPluginConfig
{
    public required string ProjectId { get; set; }
    public required string ServiceName { get; set; }
    public required string OtlpEndpoint { get; set; }
    public required OtlpExportProtocol OtlpProtocol { get; set; }
}

public class ObservabilityPlugin : Plugin
{
    public const string ObservabilityHeader = "x-highlight-request";
    private static ObservabilityPluginConfig _config;
    public ObservabilityPlugin(ObservabilityPluginConfig config) : base(config.ServiceName) {
        _config = config;
    }

    public override void Register(ILdClient client, EnvironmentMetadata metadata)
    {
        // OpenTelemetry setup is handled via the static configuration methods
    }

    public static Dictionary<string, string?> GetSessionContext()
    {
        var ctx = new Dictionary<string, string?>
        {
            {
                "highlight.project_id", _config.ProjectId
            },
            {
                "service.name", _config.ServiceName
            },
        };

        var headerValue = Baggage.Current.GetBaggage(ObservabilityHeader);
        if (headerValue == null) return ctx;

        string?[] parts = headerValue.Split("/");
        if (parts.Length < 2) return ctx;

        ctx["highlight.session_id"] = parts[0];
        // rely on `traceparent` w3c parent context propagation instead of highlight.trace_id
        return ctx;
    }

    public class TraceProcessor : BaseProcessor<Activity> {
        public override void OnStart(Activity data)
        {
            var ctx = GetSessionContext();
            foreach (var entry in ctx)
            {
                data.SetTag(entry.Key, entry.Value);
            }

            base.OnStart(data);
        }
    }

    public class LogProcessor : BaseProcessor<LogRecord> {
        public override void OnStart(LogRecord data)
        {
            var ctx = GetSessionContext();
            var attributes = ctx.Select(entry => new KeyValuePair<string, object?>(entry.Key, entry.Value)).ToList();
            if (data.Attributes != null)
            {
                attributes = attributes.Concat(data.Attributes).ToList();
            }

            data.Attributes = attributes;
            base.OnStart(data);
        }
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
                .AddProcessor(new LogProcessor())
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
                .AddProcessor(new TraceProcessor())
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