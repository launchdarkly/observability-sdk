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

/**
 * Collects sensitive screen areas that should be masked in session replay.
 *
 * This encapsulates both Jetpack Compose and native View detection logic.
 */
class SensitiveAreasCollector {

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
                if (view is ComposeView) {
                    val semanticsOwner = getSemanticsOwner(view)
                    val rootSemanticsNode = semanticsOwner?.unmergedRootSemanticsNode
                    if (rootSemanticsNode != null) {
                        val rootTarget = ComposeMaskTarget.from(view)
                        val sensitiveRects = if (rootTarget != null) {
                            findComposeSensitiveAreas(rootTarget.rootNode, rootTarget, matchers)
                        } else {
                            emptyList()
                        }
                        allSensitiveRects.addAll(sensitiveRects)
                    }
                } else {
                    val sensitiveRects = findNativeSensitiveRects(NativeMaskTarget(view), matchers)
                    allSensitiveRects.addAll(sensitiveRects)
                }
            }
        } catch (ignored: Exception) {
            // Best-effort collection; ignore failures accessing Compose internals
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
     * Gets the SemanticsOwner from a ComposeView using reflection. This is necessary because
     * AndroidComposeView and semanticsOwner are not publicly exposed.
     */
    private fun getSemanticsOwner(composeView: ComposeView): SemanticsOwner? {
        return try {
            if (composeView.isNotEmpty()) {
                val androidComposeView = composeView.getChildAt(0)

                val androidComposeViewClass =
                    Class.forName("androidx.compose.ui.platform.AndroidComposeView")
                if (androidComposeViewClass.isInstance(androidComposeView)) {
                    val field = androidComposeViewClass.getDeclaredField("semanticsOwner")
                    field.isAccessible = true
                    field.get(androidComposeView) as? SemanticsOwner
                } else {
                    null
                }
            } else {
                null
            }
        } catch (ignored: Exception) {
            null
        }
    }

    /**
     * Find sensitive Compose areas by traversing the semantic node tree.
     */
    private fun findComposeSensitiveAreas(
        rootSemanticsNode: SemanticsNode,
        maskTarget: ComposeMaskTarget,
        matchers: List<MaskMatcher>
    ): List<ComposeRect> {
        // TODO: O11Y-629 - add logic to check for sensitive areas in Compose views
        val sensitiveRects = mutableListOf<ComposeRect>()

        try {
            traverseSemanticNode(rootSemanticsNode, sensitiveRects, maskTarget, matchers)
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
