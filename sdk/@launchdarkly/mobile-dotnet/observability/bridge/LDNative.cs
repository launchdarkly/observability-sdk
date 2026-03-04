using System;
using System.Reflection;

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
        var rawVersion = typeof(LDNative).Assembly
            .GetCustomAttribute<AssemblyInformationalVersionAttribute>()
            ?.InformationalVersion ?? string.Empty;
        var observabilityVersion = rawVersion.Split('+')[0];
#if ANDROID
        var app = (Android.App.Application)global::Android.App.Application.Context;
        var bridge = new ObservabilityBridge();
        ldNative.NativeVersion = bridge.Version();
        var bridgeObservabilityOptions = observability.ToNative();
        var bridgeReplayOptions = replay.ToNative();
        bridge.Start(app, mobileKey, bridgeObservabilityOptions, bridgeReplayOptions, observabilityVersion);
#elif IOS
        var bridge = new ObservabilityBridgeClient();
        ldNative.NativeVersion = bridge.Version();
        bridge.Start(mobileKey, observability, replay, observabilityVersion);
#endif

        return ldNative;
    }
}
