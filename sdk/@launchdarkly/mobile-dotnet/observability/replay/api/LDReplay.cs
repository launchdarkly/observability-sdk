using LaunchDarkly.Sdk;

namespace LaunchDarkly.SessionReplay;

public static class LDReplay
{
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
