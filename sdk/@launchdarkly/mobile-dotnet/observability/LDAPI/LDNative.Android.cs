#if ANDROID
namespace LaunchDarkly.SessionReplay;

internal static class LDNativeAndroidMapping
{
    public static global::LDObserveAndroid.LDObservabilityOptions ToNative(this ObservabilityOptions options)
    {
        return new global::LDObserveAndroid.LDObservabilityOptions(
            options.ServiceName,
            options.ServiceVersion,
            options.OtlpEndpoint,
            options.BackendUrl,
            options.ContextFriendlyName
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
            nativePrivacy
        );
    }
}
#endif
