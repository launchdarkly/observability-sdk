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
        
        srLaunch.Start(app, mobileKey, observability.ToNative(), replay.ToNative());
#elif IOS
        SRClient.Start(mobileKey, observability, replay);
#endif

        return ldNative;
    }
}
