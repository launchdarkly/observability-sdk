package com.launchdarkly.observability.replay.masking

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsOwner
import androidx.compose.ui.semantics.getOrNull
import com.launchdarkly.observability.R
import com.launchdarkly.observability.api.LdMaskSemanticsKey
import androidx.compose.ui.geometry.Rect as ComposeRect
import androidx.core.view.isNotEmpty
import com.launchdarkly.logging.LDLogger

/**
 * Collects sensitive screen areas that should be masked in session replay.
 *
 * This encapsulates both Jetpack Compose and native View detection logic.
 */
class SensitiveAreasCollector(private val logger: LDLogger) {
    /**
     * Find sensitive areas from all views in the provided [activity].
     *
     * @return a list of rects that represent sensitive areas that need to be masked
     */
    fun collectFromActivity(activity: Activity, matchers: List<MaskMatcher>): List<ComposeRect> {
        val allSensitiveRects = mutableListOf<ComposeRect>()

        try {
            val views = findViews(activity.window.decorView)

            views.forEach { view ->
                when (view) {
                    is ComposeView ->
                        ComposeMaskTarget.from(view, logger)?.let { target ->
                            allSensitiveRects += findComposeSensitiveAreas(target, matchers)
                        }
                    else ->
                        allSensitiveRects += findNativeSensitiveRects(NativeMaskTarget(view), matchers)
                }
            }
        } catch (ignored: Exception) {
            // Best-effort collection; ignore failures accessing Compose internals
            logger.warn("Failure building sensitive rects ")
        }

        return allSensitiveRects
    }

    /**
     * Recursively find all views in the hierarchy.
     */
    private fun findViews(view: View): List<View> {
        val views = mutableListOf<View>()

        views.add(view)

        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                val child = view.getChildAt(i)
                views.addAll(findViews(child))
            }
        }

        return views
    }

    /**
     * Find sensitive Compose areas by traversing the semantic node tree.
     */
    private fun findComposeSensitiveAreas(
        maskTarget: ComposeMaskTarget,
        matchers: List<MaskMatcher>
    ): List<ComposeRect> {
        // TODO: O11Y-629 - add logic to check for sensitive areas in Compose views
        val sensitiveRects = mutableListOf<ComposeRect>()

        try {
            traverseSemanticNode(maskTarget.rootNode, sensitiveRects, maskTarget, matchers)
        } catch (ignored: Exception) {
            // Ignore issues in semantics tree traversal
        }

        return sensitiveRects
    }

    /**
     * Recursively traverse a semantic node and its children to find sensitive areas.
     */
    private fun traverseSemanticNode(
        node: SemanticsNode,
        sensitiveRects: MutableList<ComposeRect>,
        maskTarget: ComposeMaskTarget,
        matchers: List<MaskMatcher>
    ) {
        // current node target is provided as parameter
        // check ldMask() modifier; do not return early so children are still traversed
        val hasLDMask = maskTarget.hasLDMask()
        if (hasLDMask || matchers.any { it.isMatch(maskTarget) }) {
            maskTarget.maskRect()?.let { sensitiveRects.add(it) }
        }

        node.children.forEach { child ->
            val childTarget = ComposeMaskTarget(
                view = maskTarget.view,
                rootNode = maskTarget.rootNode,
                config = child.config,
                boundsInWindow = child.boundsInWindow
            )
            traverseSemanticNode(child, sensitiveRects, childTarget, matchers)
        }
    }

    /**
     * Check if a native view is sensitive and add its bounds to the list if it is.
     */
    private fun findNativeSensitiveRects(
        target: NativeMaskTarget,
        matchers: List<MaskMatcher>
    ): List<ComposeRect> {
        val sensitiveRects = mutableListOf<ComposeRect>()
        var isSensitive = target.hasLDMask()

        if (!isSensitive) {
            // Allow matchers to determine sensitivity for native views as well
            isSensitive = matchers.any { matcher -> matcher.isMatch(target) }
        }

        if (isSensitive) {
            target.maskRect()?.let { sensitiveRects.add(it) }
        }

        return sensitiveRects
    }
}
