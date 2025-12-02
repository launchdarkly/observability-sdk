package com.launchdarkly.observability.replay.masking
import android.graphics.RectF
import androidx.compose.ui.graphics.Matrix

data class Mask(
    val rect: RectF,
    val viewId: Int,
    val points: FloatArray,
    val matrix: Matrix? = null
){
    // Implemented to suppress warning
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Mask) return false
        return rect == other.rect &&
                viewId == other.viewId &&
                points.contentEquals(other.points) &&
                matrix == other.matrix
    }

    // Implemented to suppress warning
    override fun hashCode(): Int {
        var result = rect.hashCode()
        result = 31 * result + viewId
        result = 31 * result + points.contentHashCode()
        result = 31 * result + (matrix?.hashCode() ?: 0)
        return result
    }
}
