using System;
using OpenTelemetry;
using OpenTelemetry.Trace;
using OpenTelemetry.Resources;

using OTelSdk = OpenTelemetry.Sdk;

namespace LaunchDarkly.Observability;

/// <summary>
/// Thread-safe singleton that owns the OpenTelemetry TracerProvider
/// configured with <see cref="LDTraceExporter"/>.
/// </summary>
public sealed class LDTracer : IDisposable
{
    private static readonly Lazy<LDTracer> LazyInstance = new(() => new LDTracer());

    private const string ServiceName = "ld-observability";

    private readonly TracerProvider _tracerProvider;

    private LDTracer()
    {
        _tracerProvider = OTelSdk.CreateTracerProviderBuilder()
            .AddSource(ServiceName)
            .SetResourceBuilder(
                ResourceBuilder.CreateDefault()
                    .AddService(serviceName: ServiceName))
            .AddProcessor(new SimpleActivityExportProcessor(new LDTraceExporter()))
            .Build()!;

        Tracer = _tracerProvider.GetTracer(ServiceName);
    }

    /// <summary>
    /// The global singleton instance.
    /// </summary>
    public static LDTracer Instance => LazyInstance.Value;

    /// <summary>
    /// The OpenTelemetry <see cref="OpenTelemetry.Trace.Tracer"/> backed by <see cref="LDTraceExporter"/>.
    /// </summary>
    public Tracer Tracer { get; }

    public void Dispose()
    {
        _tracerProvider.Dispose();
    }
}
