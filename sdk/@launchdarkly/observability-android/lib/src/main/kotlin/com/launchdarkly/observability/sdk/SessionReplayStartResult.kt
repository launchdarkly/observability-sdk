package com.launchdarkly.observability.sdk

enum class SessionReplayStartResult(val isRunning: Boolean) {
    /** Session Replay was not installed or has not finished registering. */
    UNAVAILABLE(false),

    /** Session Replay is now running because this call started it. */
    STARTED(true),

    /** Session Replay was already running before this call. */
    ALREADY_STARTED(true),

    /** Session Replay stayed stopped because the session was sampled out. */
    SAMPLED_OUT(false),
}
