using OpenTelemetry.Logs;
using Microsoft.Extensions.Logging;

#if IOS
using Foundation;
using LDObserveMaciOS;
#elif ANDROID
using LDObserveAndroid;
#endif

namespace LaunchDarkly.Observability;

/// <summary>
/// Adapts .NET <see cref="LogRecord"/> objects into calls on the native
/// logger API, mirroring <see cref="TraceBuilderAdapter"/> for traces.
/// </summary>
internal sealed class LogBuilderAdapter
{
#if IOS
    private readonly ObjcLogger _logger;

    internal LogBuilderAdapter(ObjcLogger logger)
    {
        _logger = logger;
    }

    internal void Export(LogRecord record, bool isInternal)
    {
        var message = record.FormattedMessage
                      ?? record.Body?.ToString()
                      ?? string.Empty;

        var severity = ToSeverityNumber(record.LogLevel);

        var rawTraceId = record.TraceId.ToString();
        var rawSpanId = record.SpanId.ToString();
        string? traceId = string.IsNullOrEmpty(rawTraceId) ? null : rawTraceId;
        string? spanId = string.IsNullOrEmpty(rawSpanId) ? null : rawSpanId;

        var attrs = new NSMutableDictionary();
        if (record.Attributes != null)
        {
            foreach (var kvp in record.Attributes)
            {
                attrs[new NSString(kvp.Key)] = DictionaryTypeConverters.ToNSObject(kvp.Value);
            }
        }

        _logger.RecordLog(message, severity, traceId, spanId, isInternal, attrs);
    }

#elif ANDROID
    private readonly RealLogger _logger;

    internal LogBuilderAdapter(RealLogger logger)
    {
        _logger = logger;
    }

    internal void Export(LogRecord record, bool isInternal)
    {
        var message = record.FormattedMessage
                      ?? record.Body?.ToString()
                      ?? string.Empty;

        var severity = ToSeverityNumber(record.LogLevel);

        var rawTraceId = record.TraceId.ToString();
        var rawSpanId = record.SpanId.ToString();
        string? traceId = string.IsNullOrEmpty(rawTraceId) ? null : rawTraceId;
        string? spanId = string.IsNullOrEmpty(rawSpanId) ? null : rawSpanId;

        Dictionary<string, Java.Lang.Object>? attrs = null;
        if (record.Attributes != null)
        {
            attrs = new Dictionary<string, Java.Lang.Object>();
            foreach (var kvp in record.Attributes)
            {
                var jVal = DictionaryTypeConverters.ToJavaObject(kvp.Value);
                if (jVal != null)
                {
                    attrs[kvp.Key] = jVal;
                }
            }
        }

        _logger.RecordLog(message, severity, traceId, spanId, isInternal, attrs);
    }
#endif

    private static int ToSeverityNumber(LogLevel level) => level switch
    {
        LogLevel.Trace       => (int)Severity.Trace,
        LogLevel.Debug       => (int)Severity.Debug,
        LogLevel.Information => (int)Severity.Info,
        LogLevel.Warning     => (int)Severity.Warn,
        LogLevel.Error       => (int)Severity.Error,
        LogLevel.Critical    => (int)Severity.Fatal,
        _                    => (int)Severity.Unspecified,
    };
}
