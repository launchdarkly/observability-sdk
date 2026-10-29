using System;
using System.Collections.Generic;
using System.Reflection;
using LaunchDarkly.Observability;

#if ANDROID
using Android.App;
using LDObserveAndroid;
#elif IOS
using UIKit;
using LDObserveMaciOS;
#endif

namespace LaunchDarkly.SessionReplay;

internal class LDNative
{
    public ObservabilityOptions Observability { get; set; }
    public SessionReplayOptions Replay { get; set; }
    public String NativeVersion { get; set; } = string.Empty;

    internal static string CurrentNativeVersion { get; private set; } = string.Empty;

    private LDNative(ObservabilityOptions observability, SessionReplayOptions replay)
    {
        Observability = observability;
        Replay = replay;
    }

    private static string GetObservabilityVersion()
    {
        var rawVersion = typeof(LDNative).Assembly
            .GetCustomAttribute<AssemblyInformationalVersionAttribute>()
            ?.InformationalVersion ?? string.Empty;
        return rawVersion.Split('+')[0];
    }

    internal static LDNative Start(string mobileKey, ObservabilityOptions observability, SessionReplayOptions replay)
    {
        var ldNative = new LDNative(observability, replay);
        var observabilityVersion = GetObservabilityVersion();

        // Apply any buffered pre-init writes (e.g. LDReplay.IsEnabled) to the replay
        // options before the native bridge consumes them. Otherwise pending values set
        // during the init race would never reach native (Android converts via ToNative()
        // and iOS reads initial values during Start).
        LDReplay.OnNativeStarted(ldNative.Replay);

#if ANDROID
        var app = (Android.App.Application)global::Android.App.Application.Context;
        var bridge = new ObservabilityBridge();
        ldNative.NativeVersion = bridge.Version();
        var bridgeObservabilityOptions = observability.ToNative();
        var bridgeReplayOptions = replay.ToNative();

        // Replay buffered LDObserve.SetAttributes/RemoveAttributes calls before Start: the native
        // store accepts writes before init, so telemetry recorded during startup carries them too.
        LDObserve.OnNativeStarted(new NativeGlobalAttributesSink(bridge));
        bridge.Start(app, mobileKey, bridgeObservabilityOptions, bridgeReplayOptions, observabilityVersion);
#elif IOS
        var bridge = new ObservabilityBridgeClient();
        ldNative.NativeVersion = bridge.Version();

        LDObserve.OnNativeStarted(new NativeGlobalAttributesSink());
        bridge.Start(mobileKey, observability, replay, observabilityVersion);
#endif

        CurrentNativeVersion = ldNative.NativeVersion;

        return ldNative;
    }
}
