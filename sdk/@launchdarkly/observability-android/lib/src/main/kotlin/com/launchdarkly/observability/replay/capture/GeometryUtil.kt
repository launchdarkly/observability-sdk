package com.launchdarkly.observability.replay.capture

/**
 * Shared geometry structures used across replay capture models.
 *
 * Data classes provide value-based equality and hash semantics, so equal
 * dimensions/rectangles compare equal regardless of instance identity.
 */
data class IntRect(
    val left: Int,
    val top: Int,
    val width: Int,
    val height: Int,
)

data class IntSize(
    val width: Int,
    val height: Int,
)
