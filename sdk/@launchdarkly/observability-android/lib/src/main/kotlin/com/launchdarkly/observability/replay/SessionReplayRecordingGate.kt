package com.launchdarkly.observability.replay

/**
 * Decides whether Session Replay may take screenshots, from the recording verdicts seen during this
 * launch and the unrecoverable failure cached from a previous one.
 *
 * Kept separate from the service (like [SessionReplaySamplingSession]) so the orderings that matter — a
 * refusal arriving before start, a launch that begins with screenshots withheld, a refusal after a
 * recovery — are testable without a live service. Not thread-safe: the owner serializes access.
 *
 * Mirrors `SessionReplayRecordingGate` in the Swift session replay SDK.
 */
internal class SessionReplayRecordingGate(hasCachedFailure: Boolean) {
    /** What the owning service must do in response to a verdict. */
    sealed interface Outcome {
        /** Nothing to do: the verdict confirms the state the gate is already in. */
        data object None : Outcome

        /**
         * Screenshots were withheld and may now start. The cached failure is resolved, so it must be
         * erased for the next launch to record from the start.
         */
        data object ReleaseScreenCapture : Outcome

        /**
         * Recording must end for this launch, and [reason] must be persisted so the next launch
         * withholds screenshots until the backend has answered.
         */
        data class StopRecording(val reason: String) : Outcome
    }

    /**
     * Whether screenshots are being held back until the backend accepts the session. Starts out set when
     * a previous launch was refused.
     */
    private var isWithheld = hasCachedFailure
    private var hasFailedUnrecoverably = false

    /**
     * Screenshots are taken from the start unless a previous launch was refused, in which case the
     * backend has to accept the session first.
     */
    val isScreenCaptureAllowed: Boolean
        get() = !hasFailedUnrecoverably && !isWithheld

    /**
     * Whether recording can start at all. A refused launch cannot record again: the exporter stops
     * talking to the backend until the process is relaunched.
     */
    val canStartRecording: Boolean
        get() = !hasFailedUnrecoverably

    /**
     * Whether a verdict is still owed while screenshots are withheld, which makes a foreground worth
     * another attempt.
     */
    val isAwaitingVerdict: Boolean
        get() = !hasFailedUnrecoverably && isWithheld

    fun apply(verdict: SessionReplayInitializationVerdict): Outcome = when (verdict) {
        is SessionReplayInitializationVerdict.Allowed -> {
            // A refusal is final for this launch, and an acceptance changes nothing when screenshots are
            // already being taken.
            if (hasFailedUnrecoverably || !isWithheld) {
                Outcome.None
            } else {
                isWithheld = false
                Outcome.ReleaseScreenCapture
            }
        }

        is SessionReplayInitializationVerdict.Unrecoverable -> {
            if (hasFailedUnrecoverably) {
                Outcome.None
            } else {
                hasFailedUnrecoverably = true
                isWithheld = true
                Outcome.StopRecording(verdict.reason)
            }
        }
    }
}
