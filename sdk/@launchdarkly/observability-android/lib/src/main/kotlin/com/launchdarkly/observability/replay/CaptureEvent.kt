package com.launchdarkly.observability.replay

/**
 * Represents a capture for the replay instrumentation
 *
 * @property imageBase64 The capture encoded as a Base64 string.
 * @property origHeight The original height of the capture in pixels.
 * @property origWidth The original width of the captured in pixels.
 * @property timestamp The timestamp when the capture was taken, in milliseconds since epoch.
 * @property session The unique session identifier that this capture belongs to. This links
 *                   the capture to a specific user session.
 */
data class CaptureEvent(
    val imageBase64: String,
    val origHeight: Int,
    val origWidth: Int,
    val timestamp: Long,
    val session: String
)
