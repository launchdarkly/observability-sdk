#if IOS
using Foundation;
using LDObserveMaciOS;

namespace LaunchDarkly.SessionReplay;

public class ObservabilityBridgeClient
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

    public void Start(string mobileKey, ObservabilityOptions observability, SessionReplayOptions replay)
    {
        var objcObs = new ObjcObservabilityOptions
        {
            ServiceName = observability.ServiceName ?? "observability-maui",
            ServiceVersion = observability.ServiceVersion ?? "0.1.0",
            OtlpEndpoint = observability.OtlpEndpoint ?? "https://otel.observability.app.launchdarkly.com:4318",
            BackendUrl = observability.BackendUrl ?? "https://pub.observability.app.launchdarkly.com"
        };

        var objcReplay = new ObjcSessionReplayOptions
        {
            IsEnabled = replay.IsEnabled,
            MaskTextInputs = replay.Privacy?.MaskTextInputs ?? true,
            MaskWebViews = replay.Privacy?.MaskWebViews ?? false,
            MaskLabels = replay.Privacy?.MaskLabels ?? false,
            MaskImages = replay.Privacy?.MaskImages ?? false
        };

        _native.Start(mobileKey, objcObs, objcReplay);
    }
}
#endif
