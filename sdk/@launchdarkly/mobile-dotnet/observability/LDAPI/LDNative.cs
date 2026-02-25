using System;

#if ANDROID
using Android.App;
using LDObserveAndroid;
#elif IOS
using UIKit;
using LDObserveMaciOS;
#endif

namespace LaunchDarkly.SessionReplay;

public class LDNative
{
    public ObservabilityOptions Observability { get; set; }
    public SessionReplayOptions Replay { get; set; }
    public String NativeVersion { get; set; } = string.Empty;

    private LDNative(ObservabilityOptions observability, SessionReplayOptions replay)
    {
        Observability = observability;
        Replay = replay;
    }

    public static LDNative Start(string mobileKey, ObservabilityOptions observability, SessionReplayOptions replay)
    {
        var ldNative = new LDNative(observability, replay);
#if ANDROID
        var app = (Android.App.Application)global::Android.App.Application.Context;
        var srLaunch = new SRLaunch();
        ldNative.NativeVersion = srLaunch.Version();
        srLaunch.Start(app, mobileKey, observability.ToNative(), replay.ToNative());
#elif IOS
        var srClient = new SRClient();
        ldNative.NativeVersion = srClient.Version();
        srClient.Start(mobileKey, observability, replay);
#endif

        return ldNative;
    }
}
