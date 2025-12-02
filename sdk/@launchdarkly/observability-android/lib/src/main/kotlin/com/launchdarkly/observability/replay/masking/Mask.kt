package com.launchdarkly.observability.replay.masking
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Canvas
import android.graphics.Paint

data class Mask(
    val rect: RectF,
    val viewId: Int,
    val points: FloatArray? = null) {
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

fun Mask.draw(path: Path, canvas: Canvas, paint: Paint) {
    if (points != null) {
        val pts = points

        path.reset()
        path.moveTo(pts[0], pts[1])
        path.lineTo(pts[2], pts[3])
        path.lineTo(pts[4], pts[5])
        path.lineTo(pts[6], pts[7])
        path.close()

        canvas.drawPath(path, paint)
    } else {
        val intRect = Rect(
            rect.left.toInt(),
            rect.top.toInt(),
            rect.right.toInt(),
            rect.bottom.toInt()
        )
        canvas.drawRect(intRect, paint)
    }
}