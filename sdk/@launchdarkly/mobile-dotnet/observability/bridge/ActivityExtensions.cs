using System.Diagnostics;

#if IOS
using Foundation;
using LDObserveMaciOS;
#elif ANDROID
using LDObserveAndroid;
#endif

namespace LaunchDarkly.Observability;

/// <summary>
/// Extension methods on <see cref="Activity"/> to build platform-native
/// OpenTelemetry span representations (<c>ObjcSpan</c> / <c>SpanData</c>).
/// </summary>
internal static class ActivityExtensions
{
#if IOS
    /// <summary>
    /// Converts an <see cref="Activity"/> into an <see cref="ObjcSpan"/>
    /// that conforms to the OpenTelemetry SpanBase protocol on the Swift side.
    /// </summary>
    internal static ObjcSpan ToObjcSpan(this Activity activity)
    {
        var statusCode = activity.Status switch
        {
            ActivityStatusCode.Ok    => 1,
            ActivityStatusCode.Error => 2,
            _                        => 0,
        };

        var attributes = new NSMutableDictionary();
        foreach (var tag in activity.TagObjects)
        {
            attributes[new NSString(tag.Key)] = DictionaryTypeConverters.ToNSObject(tag.Value);
        }

        return new ObjcSpan(
            traceId: activity.TraceId.ToString(),
            spanId: activity.SpanId.ToString(),
            name: activity.DisplayName,
            statusCode: statusCode,
            attributes: attributes
        );
    }

#elif ANDROID
    /// <summary>
    /// Converts an <see cref="Activity"/> into a <see cref="SpanData"/>
    /// that can be converted to an OpenTelemetry Span on the Kotlin side via <c>toSpan()</c>.
    /// </summary>
    internal static SpanData ToSpanData(this Activity activity)
    {
        var statusCode = activity.Status switch
        {
            ActivityStatusCode.Ok    => 1,
            ActivityStatusCode.Error => 2,
            _                        => 0,
        };

        var attributes = new Dictionary<string, Java.Lang.Object>();
        foreach (var tag in activity.TagObjects)
        {
            Java.Lang.Object? jVal = tag.Value switch
            {
                string s  => new Java.Lang.String(s),
                bool b    => new Java.Lang.Boolean(b),
                int i     => new Java.Lang.Integer(i),
                long l    => new Java.Lang.Long(l),
                double d  => new Java.Lang.Double(d),
                float f   => new Java.Lang.Float(f),
                _         => tag.Value != null ? new Java.Lang.String(tag.Value.ToString()!) : null,
            };

            if (jVal != null)
                attributes[tag.Key] = jVal;
        }

        return new SpanData(
            traceIdHex: activity.TraceId.ToString(),
            spanIdHex: activity.SpanId.ToString(),
            spanName: activity.DisplayName,
            statusCodeInt: statusCode,
            spanAttributes: attributes
        );
    }
#endif
}
