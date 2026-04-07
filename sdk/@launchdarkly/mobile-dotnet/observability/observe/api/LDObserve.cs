using System.Collections.Generic;
using System.Linq;
using LaunchDarkly.Sdk;
using OpenTelemetry;
using OpenTelemetry.Trace;
using System.Diagnostics;

using OTelSdk = OpenTelemetry.Sdk;

#if IOS
using Foundation;
using LDObserveMaciOS;
#endif

namespace LaunchDarkly.Observability;

/// <summary>
/// Static facade over the native observability bridge.
/// On platforms without a native implementation, methods are no-ops.
/// </summary>
public static partial class LDObserve
{
#if ANDROID
    private static readonly LDObserveAndroid.ObservabilityBridge _androidBridge = new();
#endif

    private static readonly Tracer NoopTracer =
        OTelSdk.CreateTracerProviderBuilder().Build()!.GetTracer("noop");

    private static LDTracer? _tracer;

    internal static void InitTracer(string serviceName)
    {
        _tracer?.Dispose();
        _tracer = new LDTracer(serviceName);
    }

    // -------- Public API --------

    /// <summary>
    /// Record a log with typed severity, routed through the native logger instance.
    /// </summary>
    public static void RecordLog(string message, Severity severity, IDictionary<string, object?>? attributes = null)
    {
        string? traceId = Activity.Current?.TraceId.ToString();
        string? spanId = Activity.Current?.SpanId.ToString();

#if IOS
        var nativeLogger = LDObserveBridge.GetObjcLogger();
        if (nativeLogger == null) return;
        var dict = DictionaryTypeConverters.ToNSDictionary(attributes) ?? new NSDictionary();
        nativeLogger.RecordLog(message, (nint)severity, traceId, spanId, false, dict);
#elif ANDROID
        var nativeLogger = LDObserveAndroid.LDObserveBridgeAdapter.Logger;
        if (nativeLogger == null) return;
        var map = DictionaryTypeConverters.ToJavaDictionary(attributes);
        nativeLogger.RecordLog(message, (int)severity, traceId, spanId, false, map);
#endif
    }

    /// <summary>
    /// Record an error.
    /// </summary>
    public static void RecordError(string message, string? cause = null)
    {
#if IOS
        LDObserveBridge.RecordError(message, cause);
#elif ANDROID
        _androidBridge.RecordError(message, cause);
#endif
    }

    /// <summary>
    /// Record a gauge metric.
    /// </summary>
    public static void RecordMetric(string name, double value)
    {
#if IOS
        LDObserveBridge.RecordMetric(name, value);
#elif ANDROID
        _androidBridge.RecordMetric(name, value);
#endif
    }

    /// <summary>
    /// Record a count metric.
    /// </summary>
    public static void RecordCount(string name, double value)
    {
#if IOS
        LDObserveBridge.RecordCount(name, value);
#elif ANDROID
        _androidBridge.RecordCount(name, value);
#endif
    }

    /// <summary>
    /// Record an incremental counter metric.
    /// </summary>
    public static void RecordIncr(string name, double value)
    {
#if IOS
        LDObserveBridge.RecordIncr(name, value);
#elif ANDROID
        _androidBridge.RecordIncr(name, value);
#endif
    }

    /// <summary>
    /// Record a histogram metric.
    /// </summary>
    public static void RecordHistogram(string name, double value)
    {
#if IOS
        LDObserveBridge.RecordHistogram(name, value);
#elif ANDROID
        _androidBridge.RecordHistogram(name, value);
#endif
    }

    /// <summary>
    /// Record an up-down counter metric.
    /// </summary>
    public static void RecordUpDownCounter(string name, double value)
    {
#if IOS
        LDObserveBridge.RecordUpDownCounter(name, value);
#elif ANDROID
        _androidBridge.RecordUpDownCounter(name, value);
#endif
    }

    /// <summary>
    /// Returns the OpenTelemetry <see cref="Tracer"/> initialized during startup.
    /// Falls back to a no-op tracer before initialization.
    /// </summary>
    public static Tracer GetTracer() => _tracer?.Tracer ?? NoopTracer;

    /// <summary>
    /// Starts a new active span with the given name.
    /// </summary>
    public static TelemetrySpan StartActiveSpan(string name) => GetTracer().StartActiveSpan(name);
}
