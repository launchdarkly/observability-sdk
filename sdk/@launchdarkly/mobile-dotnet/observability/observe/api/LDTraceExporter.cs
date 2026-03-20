using System.Diagnostics;
using OpenTelemetry;

#if IOS
using LDObserveMaciOS;
#endif

namespace LaunchDarkly.Observability;

/// <summary>
/// OpenTelemetry trace exporter that forwards completed spans to the
/// native SDK tracer via <see cref="TraceBuilderAdapter"/> so they flow
/// through the native sampling and event pipeline.
/// </summary>
public sealed class LDTraceExporter : BaseExporter<Activity>
{
#if IOS
    private readonly TraceBuilderAdapter? _adapter;

    public LDTraceExporter()
    {
        var nativeTracer = LDObserveBridge.GetObjcTracer();
        _adapter = nativeTracer != null ? new TraceBuilderAdapter(nativeTracer) : null;
    }
#endif

    public override ExportResult Export(in Batch<Activity> batch)
    {
#if IOS
        if (_adapter == null)
            return ExportResult.Success;

        foreach (var activity in batch)
        {
            _adapter.Export(activity);
        }
#endif
        return ExportResult.Success;
    }
}
