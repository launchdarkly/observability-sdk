package com.launchdarkly.observability.replay.masking

import android.graphics.Matrix
import android.view.View
import android.view.ViewGroup
import com.launchdarkly.logging.LDLogger
import kotlin.collections.plusAssign
import com.launchdarkly.observability.replay.utils.locationOnScreen

/**
 * Cached class reference for AbstractComposeView, resolved once via reflection.
 * Null when Compose UI is not on the runtime classpath.
 */
private val abstractComposeViewClass: Class<*>? by lazy {
    try {
        Class.forName("androidx.compose.ui.platform.AbstractComposeView")
    } catch (_: ClassNotFoundException) {
        null
    }
}

data class MaskContext(
    val matrix: Matrix,
    val rootX: Float,
    val rootY: Float,
    val matchers: List<MaskMatcher>
)
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

        val (rootX, rootY) = root.locationOnScreen()
        val context = MaskContext(
            matrix = Matrix(),
            rootX = rootX,
            rootY = rootY,
            matchers = matchers
        )

        traverse(root, context, resultMasks)
        return resultMasks
    }

    private fun traverseCompose(view: View, context: MaskContext, masks: MutableList<Mask>) {
        val composeView = view as androidx.compose.ui.platform.AbstractComposeView
        val target = ComposeMaskTarget.from(composeView, logger)
        if (target != null) {
            traverseComposeNodes(target, context, masks)
        }

        for (i in 0 until composeView.childCount) {
            val child = composeView.getChildAt(i)
            traverse(child, context, masks)
        }
    }

    private fun traverseNative(view: View, context: MaskContext, masks: MutableList<Mask>) {
        val target = NativeMaskTarget(view)
        if (shouldMask(target, context.matchers)) {
            target.mask(context)?.let {  masks += it }
        }

        if (view !is ViewGroup) return

        for (i in 0 until view.childCount) {
            val child = view.getChildAt(i)
            traverse(child, context, masks)
        }
    }

    private fun traverse(view: View, context: MaskContext, masks: MutableList<Mask>) {
        if (!view.isShown) return

        when {
            abstractComposeViewClass?.isInstance(view) == true -> traverseCompose(view, context, masks)
            isAndroidComposeView(view) -> traverseAndroidComposeView(view, context, masks)
            else -> traverseNative(view, context, masks)
        }
    }

    /**
     * Check if a native view is sensitive and add its bounds to the list if it is.
     */
    private fun traverseComposeNodes(
        target: ComposeMaskTarget,
        context: MaskContext,
        masks: MutableList<Mask>
    ) {
        if (shouldMask(target, context.matchers)) {
            target.mask(context)?.let {  masks += it }
        }

        for (child in target.rootNode.children) {
            val childTarget = ComposeMaskTarget(
                view = target.view,
                rootNode = child,
                config = child.config,
                boundsInWindow = child.boundsInWindow
            )
            traverseComposeNodes(childTarget, context, masks)
        }
    }

    private fun shouldMask(
        target: MaskTarget,
        matchers: List<MaskMatcher>
    ): Boolean {
        return target.hasLDMask()
            || matchers.any { matcher -> matcher.isMatch(target) }
    }

    private fun isAndroidComposeView(view: View): Boolean {
        return view::class.java.name.contains("AndroidComposeView")
    }

    private fun traverseAndroidComposeView(
        view: View,
        context: MaskContext,
        masks: MutableList<Mask>
    ) {
        if (view !is ViewGroup) return

        for (i in 0 until view.childCount) {
            val child = view.getChildAt(i)
            traverse(child, context, masks)
        }
    }
}
