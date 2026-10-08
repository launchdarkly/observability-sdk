#if IOS
using System.Linq;
using Foundation;
using LDObserveMaciOS;
using LaunchDarkly.SessionReplay;
namespace LaunchDarkly.Observability;

class ObservabilityBridgeClient
{
    private readonly LDObserveMaciOS.ObservabilityBridge _native;

    public ObservabilityBridgeClient()
    {
        _native = new LDObserveMaciOS.ObservabilityBridge();
    }

    public string Version()
    {
        return _native.Version();
    }

    public void Start(string mobileKey, ObservabilityOptions observability, SessionReplayOptions replay, string observabilityVersion)
    {
        var objcObs = new ObjcObservabilityOptions
        {
            ServiceName = observability.ServiceName ?? "observability-maui",
            ServiceVersion = observability.ResolvedServiceVersion,
            OtlpEndpoint = observability.OtlpEndpoint ?? "https://otel.observability.app.launchdarkly.com:4318",
            BackendUrl = observability.BackendUrl ?? "https://pub.observability.app.launchdarkly.com",
            Attributes = DictionaryTypeConverters.ToNSDictionary(observability.Attributes),
            NetworkRequests = observability.Instrumentation.NetworkRequests,
            LaunchTimes = observability.Instrumentation.LaunchTimes,
            CustomHeaders = observability.CustomHeaders is null
                ? null
                : NSDictionary.FromObjectsAndKeys(
                    observability.CustomHeaders.Values.Select(v => (NSObject)new NSString(v)).ToArray(),
                    observability.CustomHeaders.Keys.Select(k => (NSObject)new NSString(k)).ToArray()),
            LogsApiLevel = (nint)(int)observability.LogsApiLevel
        };

        var objcReplay = new ObjcSessionReplayOptions
        {
            IsEnabled = replay.IsEnabled,
            MaskTextInputs = replay.Privacy?.MaskTextInputs ?? true,
            MaskWebViews = replay.Privacy?.MaskWebViews ?? false,
            MaskLabels = replay.Privacy?.MaskLabels ?? false,
            MaskImages = replay.Privacy?.MaskImages ?? false,
            MinimumAlpha = replay.Privacy?.MinimumAlpha ?? 0.02,
            SampleRate = replay.SampleRate,
            FrameRate = replay.FrameRate,
            Scale = replay.Scale,
            ImageQuality = replay.ImageQuality
        };

        _native.Start(mobileKey, objcObs, objcReplay, observabilityVersion);
    }
}
#endif
