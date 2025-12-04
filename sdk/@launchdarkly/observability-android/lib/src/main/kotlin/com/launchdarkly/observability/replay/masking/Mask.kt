package com.launchdarkly.observability.replay.masking

import android.graphics.RectF

data class Mask(
    val rect: RectF,
    val viewId: Int,
    val points: FloatArray? = null
) {
    // Implemented to suppress warning
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Mask) return false
        return rect == other.rect &&
                viewId == other.viewId &&
                points.contentEquals(other.points)
    }

    // Implemented to suppress warning
    override fun hashCode(): Int {
        var result = rect.hashCode()
        result = 31 * result + viewId
        result = 31 * result + points.contentHashCode()
        return result
    }
}