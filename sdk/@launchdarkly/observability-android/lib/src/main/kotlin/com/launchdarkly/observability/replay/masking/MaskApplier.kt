package com.launchdarkly.observability.replay.masking

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.Rect
import androidx.core.graphics.withScale
import kotlin.math.abs
import kotlin.math.max

class MaskApplier {
    // Matches the iOS/Flutter renderer:
    //   hull  = white 0.50 -> round(0.50 * 255) = 128 = 0x80 (area the mask swept)
    //   before = white 0.52 -> round(0.52 * 255) = 133 = 0x85 (precise position)
    private val hullMaskPaint = Paint().apply {
        color = Color.rgb(0x80, 0x80, 0x80)
        style = Paint.Style.FILL
    }
    private val beforeMaskPaint = Paint().apply {
        color = Color.rgb(0x85, 0x85, 0x85)
        style = Paint.Style.FILL
    }

    private val maskIntRect = Rect()

    fun drawMasks(canvas: Canvas, maskPairsList: List<Pair<Mask, Mask?>>, scaleFactor: Float) {
        if (maskPairsList.isEmpty()) return

        canvas.withScale(scaleFactor, scaleFactor) {
            val path = Path()
            maskPairsList.forEach { pairOfMasks ->
                drawMask(pairOfMasks, path, this)
            }
        }
    }

    private fun drawMask(pairOfMasks: Pair<Mask, Mask?>, path: Path, canvas: Canvas) {
        val (before, after) = pairOfMasks
        // When the mask drifted between the before/after passes, fill the convex
        // hull spanning both positions first so the strip it swept across is
        // covered; then always stamp the precise "before" position on top.
        if (after != null) {
            drawHull(before, after, path, canvas, hullMaskPaint)
        }
        drawMask(before, path, canvas, beforeMaskPaint)
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

    /** Fills the convex hull of the two masks' eight corners. Mirrors the iOS/Flutter `drawHull`. */
    private fun drawHull(before: Mask, after: Mask, path: Path, canvas: Canvas, paint: Paint) {
        val corners = ArrayList<PointF>(8)
        addCorners(before, corners)
        addCorners(after, corners)
        val hull = convexHull8(corners)
        if (hull.size < 3) return

        path.reset()
        path.moveTo(hull[0].x, hull[0].y)
        for (i in 1 until hull.size) {
            path.lineTo(hull[i].x, hull[i].y)
        }
        path.close()
        canvas.drawPath(path, paint)
    }

    private fun addCorners(mask: Mask, out: MutableList<PointF>) {
        val pts = mask.points
        if (pts != null) {
            out.add(PointF(pts[0], pts[1]))
            out.add(PointF(pts[2], pts[3]))
            out.add(PointF(pts[4], pts[5]))
            out.add(PointF(pts[6], pts[7]))
        } else {
            val r = mask.rect
            out.add(PointF(r.left, r.top))
            out.add(PointF(r.right, r.top))
            out.add(PointF(r.right, r.bottom))
            out.add(PointF(r.left, r.bottom))
        }
    }

    fun mergeMasksMap(
        beforeMasksMap: List<List<Mask>?>,
        afterMasksMap: List<List<Mask>?>
    ): List<List<Pair<Mask, Mask?>>?>? {
        if (afterMasksMap.count() != beforeMasksMap.count()) {
            return null
        }

        val result = buildList(beforeMasksMap.size) {
            for (i in beforeMasksMap.indices) {
                val before = beforeMasksMap[i]
                val after = afterMasksMap[i]
                if (before == null) {
                    if (after == null) {
                        add(null)
                        continue
                    } else return null
                }
                if (after != null) {
                    val merged = mergeMasks(before, after) ?: return null
                    add(merged)
                } else {
                    return null
                }
            }
        }

        return result
    }

    /**
     * Reconciles the "before" and "after" mask passes. A mask that barely moved
     * is paired with a `null` "after" (the before already covers it); a mask that
     * moved is kept as a pair so [drawMask] spans both positions with a convex
     * hull. Returns `null` (drop the frame) when the passes don't line up — counts
     * or view ids differ, or a mask drifted further than its own size so the gap
     * can't be safely covered. Mirrors the iOS/Flutter `MaskStabilizer`.
     */
    private fun mergeMasks(
        beforeMasks: List<Mask>,
        afterMasks: List<Mask>
    ): List<Pair<Mask, Mask?>>? {
        if (afterMasks.count() != beforeMasks.count()) {
            return null
        }

        if (afterMasks.isEmpty()) {
            return listOf()
        }

        val resultMasks = mutableListOf<Pair<Mask, Mask?>>()
        for ((before, after) in beforeMasks.zip(afterMasks)) {
            if (before.viewId != after.viewId) {
                return null
            }
            val dy = abs(after.rect.top - before.rect.top)
            val dx = abs(after.rect.left - before.rect.left)
            val dRight = abs(after.rect.right - before.rect.right)
            val dBottom = abs(after.rect.bottom - before.rect.bottom)

            if (max(max(dx, dy), max(dRight, dBottom)) <= MOVE_TOLERANCE) {
                // Both position and size are within tolerance; "before" already
                // covers the area. Checking all four edges (not just the
                // top-left) ensures a mask that grows in place — without its
                // top-left moving — still gets hull-drawn below instead of being
                // dropped, which would expose the newly grown region.
                resultMasks += Pair(before, null)
                continue
            }

            val coversX = dx * OVERLAP_TOLERANCE < before.rect.width() - MOVE_TOLERANCE
            val coversY = dy * OVERLAP_TOLERANCE < before.rect.height() - MOVE_TOLERANCE
            if (!coversX || !coversY) {
                // Moved further than its own size; the gap can't be safely covered.
                return null
            }

            resultMasks += Pair(before, after)
        }

        return resultMasks
    }

    private companion object {
        /** Movement under this many pixels on either axis is treated as the same position. */
        const val MOVE_TOLERANCE = 1f

        /** Slack required between the observed delta and the mask's own size before covering it with a hull. */
        const val OVERLAP_TOLERANCE = 1.1f

        /** Z-component of the cross product of OA x OB; negative for a clockwise turn (y-down). */
        private fun cross(o: PointF, a: PointF, b: PointF): Float =
            (a.x - o.x) * (b.y - o.y) - (a.y - o.y) * (b.x - o.x)

        /**
         * Convex hull of a small point cloud (~8 points) via gift-wrapping, matching
         * the iOS/Flutter `convexHull8`. Returns the input unchanged for fewer than
         * four points.
         */
        fun convexHull8(points: List<PointF>): List<PointF> {
            if (points.size < 4) {
                return points
            }

            val hull = ArrayList<PointF>(points.size)

            var startPoint = points[0]
            for (point in points) {
                if (point.x < startPoint.x) {
                    startPoint = point
                }
            }

            var currentPoint = startPoint
            do {
                hull.add(currentPoint)
                var nextPoint = points[0]

                for (candidate in points) {
                    if (nextPoint == currentPoint) {
                        nextPoint = candidate
                        continue
                    }
                    if (cross(currentPoint, candidate, nextPoint) < 0) {
                        nextPoint = candidate
                    }
                }
                currentPoint = nextPoint

                // Safety valve against a non-terminating wrap from coincident points.
                if (hull.size > points.size) {
                    break
                }
            } while (currentPoint != startPoint)

            return hull
        }
    }
}
