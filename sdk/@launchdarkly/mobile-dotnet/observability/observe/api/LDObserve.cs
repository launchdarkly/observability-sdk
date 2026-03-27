using System.Collections.Generic;
using System.Linq;
using LaunchDarkly.Sdk;
using OpenTelemetry.Trace;

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

    // -------- Public API --------

    /// <summary>
    /// Record a log with integer severity.
    /// </summary>
    private static void RecordLog(string message, int severity, IDictionary<string, object?>? attributes = null)
    {
#if IOS
        var dict = DictionaryTypeConverters.ToNSDictionary(attributes) ?? new NSDictionary();
        LDObserveBridge.RecordLog(message, severity, dict);
#elif ANDROID
        var map = DictionaryTypeConverters.ToJavaDictionary(attributes);
        _androidBridge.RecordLog(message, severity, map);
#endif
    }

    /// <summary>
    /// Record a log with typed severity.
    /// </summary>
    public static void RecordLog(string message, Severity severity, IDictionary<string, object?>? attributes = null)
        => RecordLog(message, (int)severity, attributes);

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
    /// Returns the OpenTelemetry <see cref="Tracer"/> from the <see cref="LDTracer"/> singleton.
    /// </summary>
    public static Tracer GetTracer() => LDTracer.Instance.Tracer;

    /// <summary>
    /// Starts a new active span with the given name using the singleton tracer.
    /// The returned <see cref="TelemetrySpan"/> should be disposed when the operation completes.
    /// </summary>
    public static TelemetrySpan StartActiveSpan(string name) => GetTracer().StartActiveSpan(name);
}
