package com.launchdarkly.observability.replay

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsOwner
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import com.launchdarkly.observability.R
import com.launchdarkly.observability.api.LdMaskSemanticsKey
import androidx.compose.ui.geometry.Rect as ComposeRect

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
                        val sensitiveRects = findSensitiveAreas(rootSemanticsNode, view, matchers)
                        allSensitiveRects.addAll(sensitiveRects)
                    }
                } else {
                    checkNativeView(allSensitiveRects, view, matchers)
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
            if (composeView.childCount > 0) {
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
    private fun findSensitiveAreas(
        rootSemanticsNode: SemanticsNode,
        view: ComposeView,
        matchers: List<MaskMatcher>
    ): List<ComposeRect> {
        // TODO: O11Y-629 - add logic to check for sensitive areas in Compose views
        val sensitiveRects = mutableListOf<ComposeRect>()

        // Check explicit ld_mask semantics first; if present and true, mark node as sensitive.
//        val ldMask = rootSemanticsNode.config.getOrNull(LD_MASK_SEMANTICS_KEY) == true
//        if (ldMask) {
//            addNodeBoundsRect(rootSemanticsNode, sensitiveRects)
//        } else
//        {
            // Otherwise, fall back to profile/matcher-based detection.
//            if (isMatch(rootSemanticsNode)) {
//                addNodeBoundsRect(rootSemanticsNode, sensitiveRects)
//                return sensitiveRects
//            }

//            for (matcher in matchers) {
//                if (matcher.isMatch(rootSemanticsNode)) {
//                    addNodeBoundsRect(rootSemanticsNode, sensitiveRects)
//                    break
//                }
//            }
//       }

        try {
            traverseSemanticNode(rootSemanticsNode, sensitiveRects, view, matchers)
        } catch (ignored: Exception) {
            // Ignore issues in semantics tree traversal
        }

        return sensitiveRects
    }

    fun isMatch(node: SemanticsNode): Boolean {
        val config = node.config
        return config.contains(SemanticsProperties.EditableText) ||
                config.contains(SemanticsActions.SetText) ||
                config.contains(SemanticsActions.PasteText) ||
                config.contains(SemanticsActions.InsertTextAtCursor)
    }

    /**
     * Recursively traverse a semantic node and its children to find sensitive areas.
     */
    private fun traverseSemanticNode(
        node: SemanticsNode,
        sensitiveRects: MutableList<ComposeRect>,
        view: ComposeView,
        matchers: List<MaskMatcher>
    ) {
        val ldMask = node.config.getOrNull(LdMaskSemanticsKey) == true
        if (ldMask) {
            addNodeBoundsRect(node, sensitiveRects)
            return
        }

        for (matcher in matchers) {
            if (matcher.isMatch(node)) {
                val boundsInWindow = node.boundsInWindow
                val absoluteRect = ComposeRect(
                    left = boundsInWindow.left,
                    top = boundsInWindow.top,
                    right = boundsInWindow.right,
                    bottom = boundsInWindow.bottom
                )
                sensitiveRects.add(absoluteRect)
                break
            }
        }

        node.children.forEach { child ->
            traverseSemanticNode(child, sensitiveRects, view, matchers)
        }
    }

    /**
     * Adds the node's bounds in window as a rectangle to [sensitiveRects].
     */
    private fun addNodeBoundsRect(
        node: SemanticsNode,
        sensitiveRects: MutableList<ComposeRect>
    ) {
        val boundsInWindow = node.boundsInWindow
        val absoluteRect = ComposeRect(
            left = boundsInWindow.left,
            top = boundsInWindow.top,
            right = boundsInWindow.right,
            bottom = boundsInWindow.bottom
        )
        sensitiveRects.add(absoluteRect)
    }

    /**
     * Check if a native view is sensitive and add its bounds to the list if it is.
     */
    private fun checkNativeView(
        sensitiveRects: MutableList<ComposeRect>,
        view: View,
        matchers: List<MaskMatcher>
    ) {
        val tagValue = view.getTag(R.id.ld_mask_tag) as? Boolean ?: false
        if (view is android.widget.EditText || tagValue) {
            val location = IntArray(2)
            view.getLocationInWindow(location)
            val left = location[0].toFloat()
            val top = location[1].toFloat()
            val right = left + view.width
            val bottom = top + view.height

            val absoluteRect = ComposeRect(
                left = left,
                top = top,
                right = right,
                bottom = bottom
            )
            sensitiveRects.add(absoluteRect)
        }
    }
}
