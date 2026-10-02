package com.launchdarkly.observability.client

/**
 * An app-lifecycle analytics event broadcast to in-process consumers such as Session Replay.
 *
 * Drives both the taxonomy span (`app_foreground` / `app_background`) and the Session Replay
 * timeline breadcrumb (`Foreground` / `Background`). Mirrors
 * [com.launchdarkly.observability.client.screen.ScreenViewEvent] / [TrackEvent].
 *
 * @property kind Which taxonomy lifecycle event this represents.
 * @property lifecycleState The OTel-aligned lifecycle state (e.g. `foreground`, `background`).
 * @property timestamp Event time, in milliseconds since epoch.
 */
data class AppLifecycleSignal(
    val kind: Kind,
    val lifecycleState: String?,
    val timestamp: Long = System.currentTimeMillis(),
) {
    enum class Kind {
        FOREGROUND,
        BACKGROUND,
    }
}
