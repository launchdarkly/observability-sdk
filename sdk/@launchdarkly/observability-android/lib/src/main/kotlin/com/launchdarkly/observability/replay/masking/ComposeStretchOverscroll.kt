package com.launchdarkly.observability.replay.masking

import android.os.Build
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import java.lang.reflect.Field

/**
 * Finds the edge effects behind a Compose scrollable's overscroll, so [StretchOverscroll] can
 * measure how far the rubber band is displacing content.
 *
 * A Compose scrollable keeps its effects in the modifier chain rather than on a view, reachable via
 * `LayoutInfo.getModifierInfo()`. The chain is followed by class name — every link Compose has used
 * for this is named after overscroll or edge effects (`OverscrollModifierElement`,
 * `StretchOverscrollNode`, `DrawStretchOverscrollModifier`, `AndroidEdgeEffectOverscrollEffect`,
 * `EdgeEffectWrapper`) — which keeps the search to a handful of field reads and makes an internal
 * rename degrade into no inflation rather than a wrong one.
 *
 * Only nodes that report a scroll range are searched, because building the modifier list allocates
 * and the traversal visits every semantics node.
 */
internal object ComposeStretchOverscroll {
    /**
     * Pixels a live stretch may displace content inside [node] by, or `null` when [node] isn't a
     * scrollable, isn't being overscrolled, or keeps its effects somewhere this can't follow.
     */
    fun displacementIn(node: SemanticsNode): StretchDisplacement? {
        // Below API 31 overscroll is the glow, which tints pixels without moving them.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
        if (!isScrollable(node)) return null

        val holder = edgeEffectHolderIn(node) ?: return null
        val size = node.size
        return StretchOverscroll.displacementOf(holder, size.width, size.height)
    }

    private fun isScrollable(node: SemanticsNode): Boolean {
        val config = node.config
        return config.contains(SemanticsProperties.VerticalScrollAxisRange) ||
            config.contains(SemanticsProperties.HorizontalScrollAxisRange)
    }

    /** The object holding [node]'s edge effects, or `null` when the chain leads nowhere. */
    private fun edgeEffectHolderIn(node: SemanticsNode): Any? = try {
        var holder: Any? = null
        for (info in node.layoutInfo.getModifierInfo()) {
            // Node-based modifiers keep their state in the node, which ModifierInfo exposes as
            // `extra`, while older ones keep it on the element itself.
            holder = findEdgeEffectHolder(info.modifier, depth = 0)
                ?: findEdgeEffectHolder(info.extra, depth = 0)
            if (holder != null) break
        }
        holder
    } catch (_: Throwable) {
        // Modifier internals vary across Compose versions; not finding them means no inflation.
        null
    }

    private fun findEdgeEffectHolder(value: Any?, depth: Int): Any? {
        if (value == null || depth > MAX_SEARCH_DEPTH) return null

        val type = value.javaClass
        if (!isOverscrollRelated(type)) return null
        if (StretchOverscroll.holdsEdgeEffects(type)) return value

        for (field in referenceFieldsOf(type)) {
            val child = try {
                field.get(value)
            } catch (_: Throwable) {
                continue
            }
            findEdgeEffectHolder(child, depth + 1)?.let { return it }
        }

        return null
    }

    private fun isOverscrollRelated(type: Class<*>): Boolean {
        val name = type.name.lowercase()
        return name.contains("overscroll") || name.contains("edgeeffect")
    }

    /**
     * Cache of the reflective lookup, keyed by class. Read only while collecting masks, which
     * happens on the main thread.
     */
    private val referenceFieldsByClass = HashMap<Class<*>, List<Field>>()

    /** Declared fields of [type] that can hold another object, and can be read. */
    private fun referenceFieldsOf(type: Class<*>): List<Field> =
        referenceFieldsByClass.getOrPut(type) {
            val fields = try {
                type.declaredFields
            } catch (_: Throwable) {
                return@getOrPut emptyList()
            }

            fields.filter { field ->
                if (field.type.isPrimitive) return@filter false
                try {
                    field.isAccessible = true
                    true
                } catch (_: Throwable) {
                    false
                }
            }
        }

    /**
     * Deepest chain Compose has needed: element, wrapped effect, effect, wrapper. Bounds the search
     * regardless of what the names lead into.
     */
    private const val MAX_SEARCH_DEPTH = 4
}
