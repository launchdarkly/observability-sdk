package com.launchdarkly.observability.client.screen

/**
 * A screen appearance broadcast to in-process consumers such as Session Replay.
 *
 * Emitted whenever a `screen_view` is recorded, covering both automatic Activity capture and the
 * manual `trackScreenView` API. Session Replay maps these to RRWeb `Navigate` custom events,
 * mirroring the web SDK where each path change emits `addCustomEvent('Navigate', url)`.
 *
 * @property name Human-readable screen name, i.e. the current "route".
 * @property previousName The screen shown immediately before this one, if known.
 * @property timestamp Capture time, in milliseconds since epoch.
 */
data class ScreenViewEvent(
    val name: String,
    val previousName: String?,
    val timestamp: Long,
)
