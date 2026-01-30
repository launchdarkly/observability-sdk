#if IOS
using Foundation;
using LDObserveMaciOS;

namespace LaunchDarkly.SessionReplay;

public static class SRClient
{
    public static void Start(string mobileKey, ObservabilityOptions observability, SessionReplayOptions replay)
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
            MaskImages = replay.Privacy?.MaskImages ?? false
        };

        LDObserveMaciOS.SRClient.Start(mobileKey, objcObs, objcReplay);
    }
}
#endif
