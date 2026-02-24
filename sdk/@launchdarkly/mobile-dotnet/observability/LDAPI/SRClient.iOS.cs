#if IOS
using Foundation;
using LDObserveMaciOS;

namespace LaunchDarkly.SessionReplay;

public class SRClient
{
    private readonly LDObserveMaciOS.SRClient _native;

    public SRClient()
    {
        _native = new LDObserveMaciOS.SRClient();
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
