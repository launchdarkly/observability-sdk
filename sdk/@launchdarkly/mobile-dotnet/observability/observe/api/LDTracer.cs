using System;
using System.Diagnostics;
using OpenTelemetry;
using OpenTelemetry.Trace;
using OpenTelemetry.Resources;

using OTelSdk = OpenTelemetry.Sdk;

namespace LaunchDarkly.Observability;

/// <summary>
/// Owns the OpenTelemetry TracerProvider configured with <see cref="LDTraceExporter"/>.
/// Created by <see cref="ObservabilityService"/> during initialization with the service name
/// from <see cref="ObservabilityOptions"/>.
/// </summary>
public sealed class LDTracer : IDisposable
{
    private readonly TracerProvider _tracerProvider;

    internal LDTracer(string serviceName, bool networkRequests = true)
    {
        ActivitySource = new ActivitySource(serviceName);

        var builder = OTelSdk.CreateTracerProviderBuilder()
            .AddSource(serviceName);

        if (networkRequests)
            builder.AddSource("System.Net.Http");

        _tracerProvider = builder
            .SetResourceBuilder(
                ResourceBuilder.CreateDefault()
                    .AddService(serviceName: serviceName))
            .AddProcessor(new SimpleActivityExportProcessor(new LDTraceExporter()))
            .Build()!;

        Tracer = _tracerProvider.GetTracer(serviceName);
    }

    public Tracer Tracer { get; }

    public ActivitySource ActivitySource { get; }

    public void Dispose()
    {
        ActivitySource.Dispose();
        _tracerProvider.Dispose();
    }
}
