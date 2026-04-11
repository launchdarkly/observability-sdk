using System.Collections.Generic;
using System.Diagnostics;
using OpenTelemetry.Trace;

namespace LaunchDarkly.Observability;

/// <summary>
/// Static facade over the <see cref="ObservabilityService"/>.
/// All methods are safe to call before initialization — they are no-ops
/// until <see cref="Initialize"/> is called.
/// </summary>
public static partial class LDObserve
{
    private static readonly ActivitySource NoopActivitySource = new("noop");

    private static volatile ObservabilityService? _service;

    internal static void Initialize(ObservabilityService service)
    {
        service.Initialize();
        _service = service;
    }

    // -------- Public API --------

    /// <summary>
    /// Record a log with typed severity, routed through the native logger instance.
    /// When <paramref name="spanContext"/> is provided, its trace/span IDs are used
    /// instead of the ambient <see cref="System.Diagnostics.Activity.Current"/>.
    /// </summary>
    public static void RecordLog(
        string message,
        Severity severity,
        IDictionary<string, object?>? attributes = null,
        SpanContext? spanContext = null)
        => _service?.RecordLog(message, severity, attributes, spanContext);

    /// <summary>
    /// Record an error from an <see cref="System.Exception"/>.
    /// </summary>
    public static void RecordError(
        Exception exception,
        IDictionary<string, object?>? attributes = null)
        => _service?.RecordError(exception, attributes);

    /// <summary>
    /// Record an error from a message string.
    /// </summary>
    public static void RecordError(string message, string? cause = null)
        => _service?.RecordError(message, cause);

    /// <summary>
    /// Record a gauge metric.
    /// </summary>
    public static void RecordMetric(string name, double value)
        => _service?.RecordMetric(name, value);

    /// <summary>
    /// Record a count metric.
    /// </summary>
    public static void RecordCount(string name, double value)
        => _service?.RecordCount(name, value);

    /// <summary>
    /// Record an incremental counter metric.
    /// </summary>
    public static void RecordIncr(string name, double value)
        => _service?.RecordIncr(name, value);

    /// <summary>
    /// Record a histogram metric.
    /// </summary>
    public static void RecordHistogram(string name, double value)
        => _service?.RecordHistogram(name, value);

    /// <summary>
    /// Record an up-down counter metric.
    /// </summary>
    public static void RecordUpDownCounter(string name, double value)
        => _service?.RecordUpDownCounter(name, value);

    /// <summary>
    /// Returns the OpenTelemetry <see cref="Tracer"/> initialized during startup.
    /// Falls back to a no-op tracer before initialization.
    /// </summary>
    public static Tracer GetTracer()
        => _service?.GetTracer() ?? TracerProvider.Default.GetTracer("noop");

    /// <summary>
    /// Starts a new active span with the given name.
    /// </summary>
    public static TelemetrySpan StartActiveSpan(string name)
        => _service?.StartActiveSpan(name) ?? GetTracer().StartActiveSpan(name);

    /// <summary>
    /// Starts a new active span with an explicit parent context and
    /// <see cref="SpanKind.Internal"/> kind.
    /// Use this when <see cref="Activity.Current"/> is not propagated
    /// (e.g. <c>Task.Run</c>, timer callbacks, platform event handlers).
    /// </summary>
    public static TelemetrySpan StartActiveSpan(string name, SpanContext parentContext)
        => StartActiveSpan(name, SpanKind.Internal, parentContext);

    /// <summary>
    /// Starts a new active span with an explicit parent context and span kind.
    /// Use this when <see cref="Activity.Current"/> is not propagated
    /// (e.g. <c>Task.Run</c>, timer callbacks, platform event handlers).
    /// </summary>
    public static TelemetrySpan StartActiveSpan(string name, SpanKind kind, SpanContext parentContext)
        => _service?.StartActiveSpan(name, kind, parentContext)
           ?? GetTracer().StartActiveSpan(name, kind, parentContext);

    /// <summary>
    /// Starts a new root span (no parent) with the given name.
    /// </summary>
    public static TelemetrySpan StartRootSpan(string name)
        => _service?.StartRootSpan(name) ?? GetTracer().StartRootSpan(name);

    // -------- Activity API --------

    /// <summary>
    /// Returns the <see cref="ActivitySource"/> initialized during startup.
    /// Falls back to a no-op source before initialization.
    /// </summary>
    public static ActivitySource GetActivitySource()
        => _service?.GetActivitySource() ?? NoopActivitySource;

    /// <summary>
    /// Starts a new <see cref="Activity"/> with the given name, parented to
    /// <see cref="Activity.Current"/>.
    /// Returns <c>null</c> when no listener is registered or the SDK is not yet initialized.
    /// </summary>
    public static Activity? StartActivity(string name)
        => _service?.StartActivity(name);

    /// <summary>
    /// Starts a new root <see cref="Activity"/> (no parent) with the given name.
    /// Returns <c>null</c> when no listener is registered or the SDK is not yet initialized.
    /// </summary>
    public static Activity? StartRootActivity(string name)
        => _service?.StartRootActivity(name);
}
