using OpenTelemetry;
using OpenTelemetry.Logs;

#if IOS
using LDObserveMaciOS;
#elif ANDROID
using LDObserveAndroid;
#endif

namespace LaunchDarkly.Observability;

/// <summary>
/// OpenTelemetry log exporter that forwards log records to the
/// native SDK logger via <see cref="LogBuilderAdapter"/> so they flow
/// through the native sampling and event pipeline.
/// Mirrors <see cref="LDTraceExporter"/> for traces.
/// </summary>
public sealed class LDLogExporter : BaseExporter<LogRecord>
{
    private readonly LogBuilderAdapter? _adapter;
    private readonly bool _isInternal;

    /// <param name="isInternal">
    /// When <c>true</c>, logs are dispatched to the internal logger
    /// (bypasses level-gating, supports span context).
    /// When <c>false</c>, logs are dispatched to the customer logger (level-gated).
    /// </param>
    public LDLogExporter(bool isInternal = false)
    {
        _isInternal = isInternal;
#if IOS
        var nativeLogger = LDObserveBridge.GetObjcLogger();
        _adapter = nativeLogger != null ? new LogBuilderAdapter(nativeLogger) : null;
#elif ANDROID
        var nativeLogger = LDObserveBridgeAdapter.Logger;
        _adapter = nativeLogger != null ? new LogBuilderAdapter(nativeLogger) : null;
#endif
    }

    public override ExportResult Export(in Batch<LogRecord> batch)
    {
        if (_adapter == null)
            return ExportResult.Success;

        foreach (var record in batch)
        {
            _adapter.Export(record, _isInternal);
        }

        return ExportResult.Success;
    }
}
