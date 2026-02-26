using System.Collections.Generic;
using System.Linq;

#if IOS
using UIKit;
using Foundation;
using LDObserveMaciOS;
#endif

namespace LaunchDarkly.SessionReplay;

/// <summary>
/// Static facade over the native observability bridge.
/// On platforms without a native implementation, methods are no-ops.
/// </summary>
public static class LDObserve
{
    /// <summary>
    /// Optional C# mirror of the Swift Severity raw values.
    /// Make sure the numeric values match your Swift enum.
    /// </summary>
  public enum Severity {
    /// <summary>Unspecified severity (0).</summary>
    Unspecified = 0,

    /// <summary>Trace severity (1).</summary>
    Trace = 1,

    /// <summary>Trace1 severity (2).</summary>
    Trace2 = Trace + 1,

    /// <summary>Trace3 severity (3).</summary>
    Trace3 = Trace2 + 1,

    /// <summary>Trace4 severity (4).</summary>
    Trace4 = Trace3 + 1,

    /// <summary>Debug severity (5).</summary>
    Debug = 5,

    /// <summary>Debug2 severity (6).</summary>
    Debug2 = Debug + 1,

    /// <summary>Debug3 severity (7).</summary>
    Debug3 = Debug2 + 1,

    /// <summary>Debug4 severity (8).</summary>
    Debug4 = Debug3 + 1,

    /// <summary>Info severity (9).</summary>
    Info = 9,

    /// <summary>Info2 severity (10).</summary>
    Info2 = Info + 1,

    /// <summary>Info3 severity (12).</summary>
    Info3 = Info2 + 1,

    /// <summary>Info4 severity (12).</summary>
    Info4 = Info3 + 1,

    /// <summary>Warn severity (13).</summary>
    Warn = 13,

    /// <summary>Warn2 severity (14).</summary>
    Warn2 = Warn + 1,

    /// <summary>Warn3 severity (15).</summary>
    Warn3 = Warn2 + 1,

    /// <summary>Warn4 severity (16).</summary>
    Warn4 = Warn3 + 1,

    /// <summary>Error severity (17).</summary>
    Error = 17,

    /// <summary>Error2 severity (18).</summary>
    Error2 = Error + 1,

    /// <summary>Error3 severity (19).</summary>
    Error3 = Error2 + 1,

    /// <summary>Error4 severity (20).</summary>
    Error4 = Error3 + 1,

    /// <summary>Fatal severity (21).</summary>
    Fatal = 21,

    /// <summary>Fatal2 severity (22).</summary>
    Fatal2 = Fatal + 1,

    /// <summary>Fatal3 severity (23).</summary>
    Fatal3 = Fatal2 + 1,

    /// <summary>Fatal4 severity (24).</summary>
    Fatal4 = Fatal3 + 1,
}

    // -------- Public API --------

    /// <summary>
    /// Record a log with integer severity.
    /// </summary>
    public static void RecordLog(string message, int severity, IDictionary<string, object?>? attributes = null)
    {
#if IOS
        var dict = attributes is null ? new NSDictionary() : ToNSDictionary(attributes);
        LDObserveBridge.RecordLog(message, severity, dict);
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
    }

    /// <summary>
    /// Record a gauge metric.
    /// </summary>
    public static void RecordMetric(string name, double value)
    {
    }

    /// <summary>
    /// Record a count metric.
    /// </summary>
    public static void RecordCount(string name, double value)
    {
    }

    /// <summary>
    /// Record an incremental counter metric.
    /// </summary>
    public static void RecordIncr(string name, double value)
    {
    }

    /// <summary>
    /// Record a histogram metric.
    /// </summary>
    public static void RecordHistogram(string name, double value)
    {
    }

    /// <summary>
    /// Record an up-down counter metric.
    /// </summary>
    public static void RecordUpDownCounter(string name, double value)
    {
    }

    // -------- Helpers (iOS only) --------
#if IOS
    private static NSDictionary ToNSDictionary(IDictionary<string, object?> src)
    {
        var keys = new List<NSObject>(src.Count);
        var vals = new List<NSObject>(src.Count);

        foreach (var (k, v) in src)
        {
            keys.Add(new NSString(k));
            vals.Add(ToNSObject(v));
        }

        return NSDictionary.FromObjectsAndKeys(vals.ToArray(), keys.ToArray());
    }

    private static NSObject ToNSObject(object? value)
    {
        if (value is null) return NSNull.Null;

        return value switch
        {
            string s => new NSString(s),
            bool b => NSNumber.FromBoolean(b),
            int i => NSNumber.FromInt32(i),
            long l => NSNumber.FromInt64(l),
            double d => NSNumber.FromDouble(d),
            float f => NSNumber.FromFloat(f),
            decimal m => NSNumber.FromDouble((double)m),

            // Arrays / Enumerables of primitives
            IEnumerable<string> arr    => NSArray.FromNSObjects(arr.Select(s => (NSObject)new NSString(s)).ToArray()),
            IEnumerable<bool> arr      => NSArray.FromNSObjects(arr.Select(b => (NSObject)NSNumber.FromBoolean(b)).ToArray()),
            IEnumerable<int> arr       => NSArray.FromNSObjects(arr.Select(i => (NSObject)NSNumber.FromInt32(i)).ToArray()),
            IEnumerable<long> arr      => NSArray.FromNSObjects(arr.Select(l => (NSObject)NSNumber.FromInt64(l)).ToArray()),
            IEnumerable<double> arr    => NSArray.FromNSObjects(arr.Select(d => (NSObject)NSNumber.FromDouble(d)).ToArray()),
            IEnumerable<float> arr     => NSArray.FromNSObjects(arr.Select(f => (NSObject)NSNumber.FromFloat(f)).ToArray()),
            IEnumerable<decimal> arr   => NSArray.FromNSObjects(arr.Select(m => (NSObject)NSNumber.FromDouble((double)m)).ToArray()),

            // Nested dictionaries
            IDictionary<string, object?> dict => ToNSDictionary(dict),

            // Already Foundation
            NSDictionary nsDict => nsDict,
            NSArray nsArray     => nsArray,
            NSObject nsObj      => nsObj,

            // Fallback: stringify
            _ => new NSString(value.ToString() ?? string.Empty)
        };
    }
#endif
}
