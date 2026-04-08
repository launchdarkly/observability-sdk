using System;
using System.Diagnostics;

#if IOS
using LDObserveMaciOS;
#elif ANDROID
using LDObserveAndroid;
#endif

namespace LaunchDarkly.Observability;

/// <summary>
/// Owns the <see cref="System.Diagnostics.ActivitySource"/> and an
/// <see cref="ActivityListener"/> that forwards completed spans to the
/// native SDK tracer via <see cref="TraceBuilderAdapter"/>.
/// </summary>
public sealed class LDTracer : IDisposable
{
    private readonly ActivityListener _listener;
    private readonly TraceBuilderAdapter? _adapter;

    internal LDTracer(string serviceName)
    {
        ActivitySource = new ActivitySource(serviceName);

#if IOS
        var nativeTracer = LDObserveBridge.GetObjcTracer();
        _adapter = nativeTracer != null ? new TraceBuilderAdapter(nativeTracer) : null;
#elif ANDROID
        var nativeTracer = LDObserveBridgeAdapter.Tracer;
        _adapter = nativeTracer != null ? new TraceBuilderAdapter(nativeTracer) : null;
#endif

        _listener = new ActivityListener
        {
            ShouldListenTo = source => source.Name == serviceName,
            Sample = (ref ActivityCreationOptions<ActivityContext> _) =>
                ActivitySamplingResult.AllDataAndRecorded,
            ActivityStopped = activity => _adapter?.Export(activity)
        };
        System.Diagnostics.ActivitySource.AddActivityListener(_listener);
    }

    public ActivitySource ActivitySource { get; }

    public void Dispose()
    {
        _listener.Dispose();
        ActivitySource.Dispose();
    }
}
