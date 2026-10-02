using LaunchDarkly.Sdk;

namespace LaunchDarkly.SessionReplay;

public static class LDReplay
{
    private static readonly PreInitReplayBuffer State = new();

    /// <summary>
    /// Controls whether session replay capture is currently enabled.
    /// Writes made before <see cref="LaunchDarkly.Observability.LDObserve.Init"/> completes
    /// are buffered and applied when the native stack is wired up.
    /// </summary>
    public static bool IsEnabled
    {
        get => State.IsEnabled;
        set => State.SetEnabled(value);
    }

    internal static void OnNativeStarted(SessionReplayOptions replay) => State.Bind(replay);

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
