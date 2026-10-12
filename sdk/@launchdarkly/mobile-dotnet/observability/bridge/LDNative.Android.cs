using System.Collections.Generic;
using LaunchDarkly.SessionReplay;

#if ANDROID
namespace LaunchDarkly.Observability;

internal static class LDNativeAndroidMapping
{
    public static global::LDObserveAndroid.LDObservabilityOptions ToNative(this ObservabilityOptions options)
    {
        return new global::LDObserveAndroid.LDObservabilityOptions(
            options.IsEnabled,
            options.ServiceName,
            options.ResolvedServiceVersion,
            options.OtlpEndpoint,
            options.BackendUrl,
            options.ContextFriendlyName,
            DictionaryTypeConverters.ToJavaDictionary(options.Attributes),
            options.Instrumentation.LaunchTimes,
            options.CustomHeaders is null ? null : new Dictionary<string, string>(options.CustomHeaders),
            (int)options.LogsApiLevel
        );
    }

    public static global::LDObserveAndroid.LDSessionReplayOptions ToNative(this SessionReplayOptions options)
    {
        var nativePrivacy = new global::LDObserveAndroid.LDPrivacyOptions(
            options.Privacy.MaskTextInputs,
            options.Privacy.MaskWebViews,
            options.Privacy.MaskLabels,
            options.Privacy.MaskImages,
            options.Privacy.MinimumAlpha
        );

        return new global::LDObserveAndroid.LDSessionReplayOptions(
            options.IsEnabled,
            options.ServiceName,
            nativePrivacy,
            options.SampleRate,
            options.FrameRate,
            options.Scale,
            options.ImageQuality
        );
    }
}
#endif
