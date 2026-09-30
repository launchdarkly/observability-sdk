package com.launchdarkly.observability.replay

/**
 * Outcome of a [com.launchdarkly.observability.sdk.LDReplay.start] call.
 *
 * Mirrors `SessionReplayStartResult` in the Swift session replay SDK, so a cross-platform bridge
 * can report the same outcome on both platforms.
 */
enum class SessionReplayStartResult {
    /** Session replay is not installed, or has not finished registering. */
    UNAVAILABLE,

    /** Session replay is recording because this call started it. */
    STARTED,

    /** Session replay was already recording before this call. */
    ALREADY_STARTED,

    /** Session replay stayed stopped because the session was sampled out. */
    SAMPLED_OUT,

    /**
     * Session replay stayed stopped because the backend refused this launch in a way retrying
     * cannot fix. Recording is only attempted again on the next launch, so starting again has no
     * effect.
     */
    UNRECOVERABLE_ERROR;

    /** Whether session replay is recording once this call has been applied. */
    val isRunning: Boolean
        get() = this == STARTED || this == ALREADY_STARTED
}
