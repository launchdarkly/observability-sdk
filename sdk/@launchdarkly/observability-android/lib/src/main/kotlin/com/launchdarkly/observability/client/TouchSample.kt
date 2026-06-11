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
 */
data class TouchSample(
    val action: Int,
    val x: Float,
    val y: Float,
    val timestamp: Long,
    val targetClassName: String? = null,
    val targetText: String? = null,
    val targetResourceId: String? = null,
)
