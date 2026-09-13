package com.launchdarkly.observability.replay.masking

import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.widget.EdgeEffect
import androidx.annotation.RequiresApi
import java.lang.reflect.Field
import kotlin.math.E
import kotlin.math.exp

/** How far, in pixels, a live stretch may displace content along each axis. */
internal data class StretchDisplacement(val dx: Float, val dy: Float)

/**
 * Reads the state of Android 12+ stretch overscroll — the "rubber band" at the end of a scrollable
 * — so masks can be grown to cover it.
 *
 * The stretch is the one animation nothing in the view tree describes. [EdgeEffect] records a
 * `RenderNode.stretch` into the container's display list and the RenderThread distorts the
 * already-recorded pixels: no view moves, no layout changes, and Compose's `boundsInWindow` is
 * equally unaware. Sampling geometry in lockstep with the drawn frame (see
 * [com.launchdarkly.observability.replay.capture.FrameSynchronizer]) doesn't help here either,
 * because the geometry isn't late — it faithfully describes an undistorted layout that was never
 * rendered. All that's left is to ask the effect how hard it is pulling and grow the masks to match.
 *
 * Containers keep their effects in private fields, so they are found reflectively. Only androidx and
 * app classes are searched, deliberately: the framework's own scrollables keep theirs in non-SDK
 * fields that reflection is blocked from on modern Android, and probing them would spam the log for
 * nothing. `RecyclerView`, `ViewPager2`, `NestedScrollView`, Compose (via [ComposeStretchOverscroll])
 * and app-defined containers are all reachable.
 */
internal object StretchOverscroll {
    /**
     * Pixels a live stretch may displace content inside [container] by, or `null` when nothing is
     * being stretched — the case for every frame outside the few around an overscroll gesture.
     */
    fun displacementIn(container: View): StretchDisplacement? {
        // Below API 31 overscroll is the glow, which tints pixels without moving them.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
        // Only containers scroll, and this runs for every view in the tree, so bail out early.
        if (container !is ViewGroup) return null
        if (container.overScrollMode == View.OVER_SCROLL_NEVER) return null

        return displacementOf(container, container.width, container.height)
    }

    /**
     * Pixels a live stretch held by [holder] may displace content by, given the size of the
     * container it belongs to. [holder] is the object owning the [EdgeEffect] fields: the scroll
     * container itself for views, or Compose's edge effect wrapper.
     *
     * Callers must have checked that stretch overscroll exists on this device.
     */
    @RequiresApi(Build.VERSION_CODES.S)
    fun displacementOf(holder: Any, width: Int, height: Int): StretchDisplacement? {
        val fields = stretchFieldsOf(holder.javaClass)
        if (fields.isEmpty()) return null

        var horizontalDistance = 0f
        var verticalDistance = 0f
        for (stretchField in fields) {
            val effect = try {
                stretchField.field.get(holder) as? EdgeEffect ?: continue
            } catch (_: Throwable) {
                continue
            }

            // Effects exist for every edge but only report a distance while actually pulled.
            val distance = pullDistanceOf(effect)
            if (distance <= 0f) continue
            if (stretchField.axis != Axis.VERTICAL) {
                horizontalDistance = maxOf(horizontalDistance, distance)
            }
            if (stretchField.axis != Axis.HORIZONTAL) {
                verticalDistance = maxOf(verticalDistance, distance)
            }
        }

        return displacement(horizontalDistance, verticalDistance, width, height)
    }

    /**
     * Turns pull distances into pixels, given the container they were pulled in. Returns `null` when
     * neither axis is pulled far enough to matter.
     *
     * @param horizontalDistance largest pull along x, in the 0..1 units [EdgeEffect.getDistance]
     *     reports.
     * @param verticalDistance largest pull along y.
     */
    fun displacement(
        horizontalDistance: Float,
        verticalDistance: Float,
        width: Int,
        height: Int
    ): StretchDisplacement? {
        if (horizontalDistance <= 0f && verticalDistance <= 0f) return null

        val dx = stretchFraction(horizontalDistance) * width
        val dy = stretchFraction(verticalDistance) * height
        if (dx < MIN_DISPLACEMENT && dy < MIN_DISPLACEMENT) return null

        return StretchDisplacement(dx = dx, dy = dy)
    }

    /** Whether instances of [type] declare readable [EdgeEffect] fields. */
    fun holdsEdgeEffects(type: Class<*>): Boolean = stretchFieldsOf(type).isNotEmpty()

    /** How hard [effect] is being pulled, as a fraction of the container it guards. */
    @RequiresApi(Build.VERSION_CODES.S)
    private fun pullDistanceOf(effect: EdgeEffect): Float = try {
        effect.distance
    } catch (_: Throwable) {
        0f
    }

    /**
     * Fraction of a container's size that content is displaced by at pull distance [distance].
     *
     * Mirrors `EdgeEffect.dampStretchVector`, whose result is what the framework hands to
     * `RenderNode.stretch` alongside the container's width and height as the pixel scale. The
     * expression below is kept in the framework's shape, constant for constant, so it can be diffed
     * against the original — including the `Math.E` in its scalar, which is easy to mistake for a
     * stray factor.
     *
     * The curve rises steeply: a third of a pull already spends two thirds of the effect, and a
     * tenth spends a third of it. Scaling linearly with [distance] instead would undercover by
     * roughly a factor of three exactly the gentle overscrolls that are the common case. Results
     * carry [SAFETY_MARGIN] of slack on top, so a future retune of the curve degrades into slightly
     * oversized masks rather than exposed content.
     */
    private fun stretchFraction(distance: Float): Float {
        if (distance <= 0f) return 0f

        val pull = distance.coerceAtMost(1f)
        val linearIntensity = LINEAR_STRETCH_INTENSITY * pull
        val scalar = E / SCROLL_DIST_AFFECTED_BY_EXP_STRETCH
        val expIntensity = EXP_STRETCH_INTENSITY * (1 - exp(-pull * scalar))
        return ((linearIntensity + expIntensity) * SAFETY_MARGIN).toFloat()
    }

    /** Axis a stretch distorts content along. */
    private enum class Axis { HORIZONTAL, VERTICAL, BOTH }

    /** An [EdgeEffect]-typed field together with the axis its name places it on. */
    private class StretchField(val field: Field, val axis: Axis)

    /**
     * Cache of the reflective lookup, keyed by the class holding the effects. Read only while
     * collecting masks, which happens on the main thread.
     */
    private val stretchFieldsByClass = HashMap<Class<*>, List<StretchField>>()

    private fun stretchFieldsOf(type: Class<*>): List<StretchField> =
        stretchFieldsByClass.getOrPut(type) {
            val found = mutableListOf<StretchField>()
            var current: Class<*>? = type
            while (current != null && !isOutOfReach(current)) {
                val fields = try {
                    current.declaredFields
                } catch (_: Throwable) {
                    emptyArray()
                }
                for (field in fields) {
                    if (!EdgeEffect::class.java.isAssignableFrom(field.type)) continue
                    val accessible = try {
                        field.isAccessible = true
                        true
                    } catch (_: Throwable) {
                        false
                    }
                    if (accessible) found += StretchField(field, axisOf(field.name))
                }
                current = current.superclass
            }
            found
        }

    /**
     * Whether [type] is a platform class. Reaching into these is both blocked as non-SDK usage and
     * pointless, since the walk starts at an app, androidx or Compose class.
     */
    private fun isOutOfReach(type: Class<*>): Boolean =
        type.name.startsWith("android.") || type == Any::class.java

    /**
     * The axis a field's name places its effect on. Containers name their effects after the edge
     * they guard — `mTopGlow` on `RecyclerView`, `mEdgeGlowLeft` on `NestedScrollView`,
     * `bottomEffect` on Compose's wrapper — and an unrecognized name counts for both axes, so an
     * unfamiliar container over-masks rather than under-masks.
     */
    private fun axisOf(fieldName: String): Axis {
        val name = fieldName.lowercase()
        val vertical = name.contains("top") || name.contains("bottom")
        val horizontal = name.contains("left") || name.contains("right")
        return when {
            vertical && !horizontal -> Axis.VERTICAL
            horizontal && !vertical -> Axis.HORIZONTAL
            else -> Axis.BOTH
        }
    }

    // From AOSP's android.widget.EdgeEffect, where the stretch caps at their sum, 3.2% of the
    // container.
    private const val LINEAR_STRETCH_INTENSITY = 0.016
    private const val EXP_STRETCH_INTENSITY = 0.016
    private const val SCROLL_DIST_AFFECTED_BY_EXP_STRETCH = 0.33

    /** Slack over the framework's own curve, so masks err on the side of covering too much. */
    private const val SAFETY_MARGIN = 1.25

    /** Sub-pixel stretches are not worth growing a mask for. */
    private const val MIN_DISPLACEMENT = 1f
}
