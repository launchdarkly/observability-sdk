using System.Diagnostics;
using OpenTelemetry;

#if IOS
using LDObserveMaciOS;
#elif ANDROID
using LDObserveAndroid;
#endif

namespace LaunchDarkly.Observability;

/// <summary>
/// OpenTelemetry trace exporter that forwards completed spans to the
/// native SDK tracer via <see cref="TraceBuilderAdapter"/> so they flow
/// through the native sampling and event pipeline.
/// </summary>
public sealed class LDTraceExporter : BaseExporter<Activity>
{
    private readonly TraceBuilderAdapter? _adapter;

    public LDTraceExporter()
    {
#if IOS
        var nativeTracer = LDObserveBridge.GetObjcTracer();
        _adapter = nativeTracer != null ? new TraceBuilderAdapter(nativeTracer) : null;
#elif ANDROID
        var nativeTracer = LDObserveBridgeAdapter.Tracer;
        _adapter = nativeTracer != null ? new TraceBuilderAdapter(nativeTracer) : null;
#endif
    }

    public override ExportResult Export(in Batch<Activity> batch)
    {
        if (_adapter == null)
            return ExportResult.Success;

        foreach (var activity in batch)
        {
            _adapter.Export(activity);
        }

        return ExportResult.Success;
    }
}
