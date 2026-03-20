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
/// OpenTelemetry span representations (<c>ObjcSpan</c> / <c>KotlinSpan</c>).
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
    /// Converts an <see cref="Activity"/> into a <see cref="KotlinSpan"/>
    /// that implements <c>io.opentelemetry.api.trace.Span</c> on the Kotlin side.
    /// </summary>
    internal static KotlinSpan ToKotlinSpan(this Activity activity)
    {
        var statusCode = activity.Status switch
        {
            ActivityStatusCode.Ok    => 1,
            ActivityStatusCode.Error => 2,
            _                        => 0,
        };

        var attributes = new Java.Util.HashMap();
        foreach (var tag in activity.TagObjects)
        {
            var jKey = new Java.Lang.String(tag.Key);
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
                attributes.Put(jKey, jVal);
        }

        return new KotlinSpan(
            traceIdHex: activity.TraceId.ToString(),
            spanIdHex: activity.SpanId.ToString(),
            spanName: activity.DisplayName,
            statusCodeInt: statusCode,
            spanAttributes: attributes
        );
    }
#endif
}
