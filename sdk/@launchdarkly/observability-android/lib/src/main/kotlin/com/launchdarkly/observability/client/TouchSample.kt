package com.launchdarkly.observability.client

import android.view.MotionEvent

/**
 * A single raw (unscaled) touch sample for the watched pointer, produced by
 * [UserInteractionManager] and consumed by Observability (tap detection) and Session Replay.
 *
 * @property action one of [MotionEvent.ACTION_DOWN], [MotionEvent.ACTION_UP] (CANCEL is reported
 *   as UP) or [MotionEvent.ACTION_MOVE].
 * @property x raw x coordinate in pixels.
 * @property y raw y coordinate in pixels.
 * @property timestamp epoch milliseconds for the sample.
 * @property targetClassName fully-qualified class name of the view under the touch (ACTION_DOWN
 *   only), or null when no target could be resolved.
 * @property targetText visible text / content description of the target view (truncated), if any.
 * @property targetResourceId the target view's resource entry name (the `accessibilityIdentifier`
 *   analog), if any.
 * @property screenId stable id (`event.screen_id`) of the active screen, read on the main thread at
 *   capture time (ACTION_DOWN) - before the touch is dispatched to app handlers that may navigate -
 *   so the click correlates with the screen the user actually tapped, not a destination screen.
 * @property screenName human-readable name (`event.screen_name`) of that screen. See [screenId].
 */
data class TouchSample(
    val action: Int,
    val x: Float,
    val y: Float,
    val timestamp: Long,
    val targetClassName: String? = null,
    val targetText: String? = null,
    val targetResourceId: String? = null,
    val screenId: String? = null,
    val screenName: String? = null,
)
