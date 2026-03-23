using System.Diagnostics;

#if IOS
using Foundation;
using LDObserveMaciOS;
#elif ANDROID
using LDObserveAndroid;
#endif

namespace LaunchDarkly.Observability;

/// <summary>
/// Adapts .NET <see cref="Activity"/> objects into calls on the native
/// tracer builder API, which creates real OpenTelemetry spans on the
/// native side and feeds them through the native pipeline.
/// </summary>
internal sealed class TraceBuilderAdapter
{
#if IOS
    private readonly ObjcTracer _tracer;

    internal TraceBuilderAdapter(ObjcTracer tracer)
    {
        _tracer = tracer;
    }

    internal void Export(Activity activity)
    {
        var startTime = new DateTimeOffset(activity.StartTimeUtc, TimeSpan.Zero)
            .ToUnixTimeMilliseconds() / 1000.0;
        var endTime = startTime + activity.Duration.TotalSeconds;

        var builder = _tracer.SpanBuilder(
            activity.DisplayName,
            startTime,
            activity.TraceId.ToString(),
            activity.ParentSpanId.ToString()
        );

        foreach (var tag in activity.TagObjects)
        {
            builder.SetAttribute(tag.Key, DictionaryTypeConverters.ToNSObject(tag.Value));
        }

        foreach (var evt in activity.Events)
        {
            if (evt.Tags.Any())
            {
                var eventAttrs = new NSMutableDictionary();
                foreach (var tag in evt.Tags)
                {
                    eventAttrs[new NSString(tag.Key)] = DictionaryTypeConverters.ToNSObject(tag.Value);
                }
                builder.AddEvent(evt.Name, eventAttrs);
            }
            else
            {
                builder.AddEvent(evt.Name);
            }
        }

        var statusCode = activity.Status switch
        {
            ActivityStatusCode.Ok    => 1,
            ActivityStatusCode.Error => 2,
            _                        => 0,
        };
        if (statusCode != 0)
        {
            builder.SetStatus(statusCode);
        }

        builder.End(endTime);
    }

#elif ANDROID
    private readonly RealTracer _tracer;

    internal TraceBuilderAdapter(RealTracer tracer)
    {
        _tracer = tracer;
    }

    internal void Export(Activity activity)
    {
        var startTime = new DateTimeOffset(activity.StartTimeUtc, TimeSpan.Zero)
            .ToUnixTimeMilliseconds() / 1000.0;
        var endTime = startTime + activity.Duration.TotalSeconds;

        var builder = _tracer.SpanBuilder(
            activity.DisplayName,
            startTime,
            activity.TraceId.ToString(),
            activity.ParentSpanId.ToString()
        );

        foreach (var tag in activity.TagObjects)
        {
            var jVal = DictionaryTypeConverters.ToJavaObject(tag.Value);
            if (jVal != null)
            {
                builder.SetAttribute(tag.Key, jVal);
            }
        }

        foreach (var evt in activity.Events)
        {
            if (evt.Tags.Any())
            {
                var eventAttrs = new Dictionary<string, Java.Lang.Object>();
                foreach (var tag in evt.Tags)
                {
                    var jVal = DictionaryTypeConverters.ToJavaObject(tag.Value);
                    if (jVal != null)
                    {
                        eventAttrs[tag.Key] = jVal;
                    }
                }
                builder.AddEventWithAttributes(evt.Name, eventAttrs);
            }
            else
            {
                builder.AddEvent(evt.Name);
            }
        }

        var statusCode = activity.Status switch
        {
            ActivityStatusCode.Ok    => 1,
            ActivityStatusCode.Error => 2,
            _                        => 0,
        };
        if (statusCode != 0)
        {
            builder.SetStatus(statusCode);
        }

        builder.End(endTime);
    }
#endif
}
