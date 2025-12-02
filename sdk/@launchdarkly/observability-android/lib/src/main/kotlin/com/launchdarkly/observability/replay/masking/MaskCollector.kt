package com.launchdarkly.observability.replay.masking

import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.AbstractComposeView
import com.launchdarkly.logging.LDLogger
import kotlin.collections.plusAssign

/**
 * Collects sensitive screen areas that should be masked in session replay.
 *
 * This encapsulates both Jetpack Compose and native View detection logic.
 */
class MaskCollector(private val logger: LDLogger) {
    /**
     * Find sensitive areas from all views in the provided [root] view.
     *
     * @return a list of masks that represent sensitive areas that need to be masked
     */
    fun collectMasks(root: View, matchers: List<MaskMatcher>): List<Mask> {
        val resultMasks = mutableListOf<Mask>()
//      TODO: use matrix to calculate final coordinates the will be close to truth in animations
//        val matrix = Matrix()
//        val (rootX, rootY) = root.locationOnScreen()

        traverse(root, matchers, resultMasks)
        return resultMasks
    }

    fun traverseCompose(view: AbstractComposeView, matchers: List<MaskMatcher>, masks: MutableList<Mask>) {
        val target = ComposeMaskTarget.from(view, logger)
        if (target != null) {
            traverseComposeNodes(target, matchers, masks)
        }

        for (i in 0 until view.childCount) {
            val child = view.getChildAt(i)
            traverse(child, matchers, masks)
        }
    }

    fun traverseNative(view: View, matchers: List<MaskMatcher>, masks: MutableList<Mask>) {
        val target = NativeMaskTarget(view)
        if (shouldMask(target, matchers)) {
            masks += target.mask()
        }

        if (view !is ViewGroup) return

        for (i in 0 until view.childCount) {
            val child = view.getChildAt(i)
            traverse(child, matchers, masks)
        }
    }

    fun traverse(view: View, matchers: List<MaskMatcher>, masks: MutableList<Mask>) {
        if (!view.isShown) return

        if (view is AbstractComposeView) {
            traverseCompose(view, matchers, masks)
        } else if (!view::class.java.name.contains("AndroidComposeView")) {
            traverseNative(view, matchers, masks)
        }
    }

    /**
     * Check if a native view is sensitive and add its bounds to the list if it is.
     */
    private fun traverseComposeNodes(
        target: ComposeMaskTarget,
        matchers: List<MaskMatcher>,
        masks: MutableList<Mask>
    ) {
        if (shouldMask(target, matchers)) {
            masks += target.mask()
        }

        for (child in target.rootNode.children) {
            val childTarget = ComposeMaskTarget(
                view = target.view,
                rootNode = child,
                config = child.config,
                boundsInWindow = child.boundsInWindow
            )
            traverseComposeNodes(childTarget, matchers, masks)
        }
    }

    private fun shouldMask(
        target: MaskTarget,
        matchers: List<MaskMatcher>
    ): Boolean {
        return target.hasLDMask()
            || matchers.any { matcher -> matcher.isMatch(target) }
    }
}
