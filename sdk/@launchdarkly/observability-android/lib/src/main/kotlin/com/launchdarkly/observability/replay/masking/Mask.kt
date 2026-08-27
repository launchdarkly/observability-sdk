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

/**
 * Grows this mask by [dx] on both sides horizontally and [dy] vertically.
 *
 * Used to cover a distortion whose direction the view tree doesn't reveal, so it grows both ways
 * rather than guessing which one the content moved. Corners of a transformed mask are pushed away
 * from its center, keeping the quad's shape while enlarging it.
 *
 * Grows the geometry in place, which is safe because both the rect and the corners are built fresh
 * for every mask on every collection pass and shared with nothing.
 */
internal fun Mask.inflate(dx: Float, dy: Float) {
    rect.left -= dx
    rect.top -= dy
    rect.right += dx
    rect.bottom += dy

    val corners = points ?: return

    var centerX = 0f
    var centerY = 0f
    for (i in corners.indices step 2) {
        centerX += corners[i]
        centerY += corners[i + 1]
    }
    centerX /= corners.size / 2
    centerY /= corners.size / 2

    for (i in corners.indices step 2) {
        corners[i] += if (corners[i] < centerX) -dx else dx
        corners[i + 1] += if (corners[i + 1] < centerY) -dy else dy
    }
}