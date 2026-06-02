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
 */
data class TouchSample(
    val action: Int,
    val x: Float,
    val y: Float,
    val timestamp: Long,
)
