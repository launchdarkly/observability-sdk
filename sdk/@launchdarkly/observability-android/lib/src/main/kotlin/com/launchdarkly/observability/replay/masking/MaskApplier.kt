package com.launchdarkly.observability.replay.masking

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import kotlin.math.abs

class MaskApplier {
    private val beforeMaskPaint = Paint().apply {
        color = Color.DKGRAY
        style = Paint.Style.FILL
    }
    private val afterMaskPaint = Paint().apply {
        color = Color.GRAY
        style = Paint.Style.FILL
    }

    private val maskIntRect = Rect()
    private val path = Path()

    fun drawMasks(canvas: Canvas, beforeMasks: List<Mask>?, afterMasks: List<Mask>?) {
        if (afterMasks == null && beforeMasks == null) return

        beforeMasks?.forEach { mask ->
            drawMask(mask, path, canvas, beforeMaskPaint)
        }
        afterMasks?.forEach { mask ->
            drawMask(mask, path, canvas, afterMaskPaint)
        }
    }

    private fun drawMask(mask: Mask, path: Path, canvas: Canvas, paint: Paint) {
        if (mask.points != null) {
            val pts = mask.points

            path.reset()
            path.moveTo(pts[0], pts[1])
            path.lineTo(pts[2], pts[3])
            path.lineTo(pts[4], pts[5])
            path.lineTo(pts[6], pts[7])
            path.close()

            canvas.drawPath(path, paint)
        } else {
            maskIntRect.left = mask.rect.left.toInt()
            maskIntRect.top = mask.rect.top.toInt()
            maskIntRect.right = mask.rect.right.toInt()
            maskIntRect.bottom =  mask.rect.bottom.toInt()
            canvas.drawRect(maskIntRect, paint)
        }
    }

    fun mergeMasksMap(
        beforeMasksMap: List<List<Mask>?>,
        afterMasksMap: List<List<Mask>?>
    ): MutableList<List<Mask>?>? {
        if (afterMasksMap.count() != beforeMasksMap.count()) {
            return null
        }

        val result: MutableList<List<Mask>?> = MutableList(beforeMasksMap.size) { null }
        for (i in beforeMasksMap.indices) {
            val before = beforeMasksMap[i]
            val after = afterMasksMap[i]
            if (before == null) {
                if (after == null) continue
                else return null
            }
            if (after != null) {
                val merged = mergeMasks(before, after) ?: return null
                result[i] = merged
            }
        }

        return result
    }

    // Check if masks are stable and returns null if not
    private fun mergeMasks(
        beforeMasks: List<Mask>,
        afterMasks: List<Mask>
    ): List<Mask>? {
        if (afterMasks.count() != beforeMasks.count()) {
            return null
        }

        if (afterMasks.count() == 0) {
            return listOf()
        }

        val stabilityTolerance = 40f
        val resultMasks = mutableListOf<Mask>()
        for ((before, after) in beforeMasks.zip(afterMasks)) {
            if (before.viewId != after.viewId) {
                return null
            }
            val diff = abs(after.rect.top - before.rect.top)
            if (diff > stabilityTolerance) {
                return null
            }
            resultMasks += before
        }

        return resultMasks
    }
}