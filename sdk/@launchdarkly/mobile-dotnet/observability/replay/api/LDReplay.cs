using LaunchDarkly.Sdk;

namespace LaunchDarkly.SessionReplay;

public static class LDReplay
{
    /// <summary>
    /// Controls whether session replay capture is currently enabled.
    /// Has no effect until the native stack has been started via
    /// <see cref="LaunchDarkly.Observability.LDObserve.Init"/>.
    /// </summary>
    public static bool IsEnabled
    {
        get => LDNative.Current?.Replay.IsEnabled ?? false;
        set
        {
            if (LDNative.Current is { } native)
                native.Replay.IsEnabled = value;
        }
    }

    /// <summary>
    /// Tracks a flag evaluation result for session replay annotation.
    /// </summary>
    public static void TrackEvaluation(string flagKey, LdValue value, int? variationIndex, EvaluationReason? reason)
    {
#if ANDROID
        // TODO: forward to Android session replay bridge
#elif IOS
        // TODO: forward to iOS session replay bridge
#endif
    }
}
