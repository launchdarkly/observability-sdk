package com.launchdarkly.observability.replay.masking

import android.graphics.Matrix
import android.view.View
import android.view.ViewGroup
import com.launchdarkly.observability.context.ObserveLogger
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

/**
 * Per-call constants for a single [MaskCollector.collectMasks] traversal.
 *
 * @param matrix scratch matrix reused while computing per-view transformations.
 * @param rootX x-coordinate of the root view in screen space; used to translate window-relative
 *     points back into root-relative coordinates.
 * @param rootY y-coordinate of the root view in screen space.
 * @param explicitMaskMatchers matchers whose match counts as an explicit mask signal that
 *     propagates to descendants (e.g. `PrivacyProfile.explicitMaskMatchers`).
 * @param globalMaskMatchers matchers whose match applies only to the matched view itself; they do
 *     not propagate to descendants and do not override an explicit unmask.
 */
data class MaskContext(
    val matrix: Matrix,
    val rootX: Float,
    val rootY: Float,
    val explicitMaskMatchers: List<MaskMatcher>,
    val globalMaskMatchers: List<MaskMatcher>,
)
/**
 * Collects sensitive screen areas that should be masked in session replay.
 *
 * This encapsulates both Jetpack Compose and native View detection logic.
 *
 * # Precedence
 *
 * For each target the collector evaluates these rules in order, stopping at the first that
 * applies:
 *
 *  1. **Explicit Masking (highest priority).** Is the target — or any of its ancestors —
 *     explicitly masked (via [MaskTarget.hasLDMask] or matched by any
 *     [MaskContext.explicitMaskMatchers] entry)? If so, the target is masked.
 *  2. **Explicit Unmasking.** Is the target — or any of its ancestors — explicitly unmasked
 *     (via [MaskTarget.hasLDUnmask])? If so, the target is not masked.
 *  3. **Global configuration.** Does any [MaskContext.globalMaskMatchers] entry match the
 *     target? If so, the target is masked. Global matches do not propagate to descendants.
 *
 * If multiple rules at the same level conflict (e.g. the same view is both `ldMask`-tagged and
 * `ldUnmask`-tagged), mask wins over unmask.
 */
class MaskCollector(private val logger: ObserveLogger) {
    /**
     * Find sensitive areas from all views under [root] and return a list of masks describing
     * regions that should be redacted in the recorded frame.
     *
     * @param root root view of the window being captured; traversal walks its descendants.
     * @param explicitMaskMatchers matchers whose match counts as an explicit mask signal that
     *     propagates to descendants. Pass an empty list when no identifier-based masking is
     *     configured.
     * @param globalMaskMatchers matchers whose match applies only to the matched view itself.
     */
    fun collectMasks(
        root: View,
        explicitMaskMatchers: List<MaskMatcher>,
        globalMaskMatchers: List<MaskMatcher>,
    ): List<Mask> {
        val resultMasks = mutableListOf<Mask>()

        val (rootX, rootY) = root.locationOnScreen()
        val context = MaskContext(
            matrix = Matrix(),
            rootX = rootX,
            rootY = rootY,
            explicitMaskMatchers = explicitMaskMatchers,
            globalMaskMatchers = globalMaskMatchers,
        )

        traverse(root, inherited = null, context, resultMasks)
        return resultMasks
    }

    /**
     * Dispatcher for one view in the traversal: hands off to the right traversal variant for
     * Compose host views, AndroidComposeView wrappers, or plain native Views. Skips views that
     * aren't currently shown.
     *
     * @param view the view to visit.
     * @param inherited the resolved-explicit state passed down from the nearest ancestor — `true`
     *     for inherited mask, `false` for inherited unmask, `null` for no inherited signal.
     * @param context per-call constants and matcher configuration.
     * @param masks output list; new mask entries are appended to it.
     */
    private fun traverse(view: View, inherited: Boolean?, context: MaskContext, masks: MutableList<Mask>) {
        if (!view.isShown) return

        when {
            abstractComposeViewClass?.isInstance(view) == true -> traverseCompose(view, inherited, context, masks)
            isAndroidComposeView(view) -> traverseAndroidComposeView(view, inherited, context, masks)
            else -> traverseNative(view, inherited, context, masks)
        }
    }

    /**
     * Visits a Compose host view: walks its semantics tree to evaluate compose nodes, then
     * recurses into any non-compose child views. The compose host itself produces no masking
     * signal of its own, so [inherited] is passed through unchanged.
     *
     * The parameter type is [View] (not `AbstractComposeView`) so this file's signatures don't
     * reference a Compose UI class. That keeps the JVM verifier from trying to load Compose
     * symbols when MaskCollector loads in apps that don't pull in Compose UI; the cast inside the
     * method body is only reached after the dispatcher in [traverse] has confirmed Compose is on
     * the classpath via [abstractComposeViewClass].
     *
     * @param view a view that is an instance of `AbstractComposeView`.
     * @param inherited see [traverse].
     * @param context see [traverse].
     * @param masks see [traverse].
     */
    private fun traverseCompose(view: View, inherited: Boolean?, context: MaskContext, masks: MutableList<Mask>) {
        val composeView = view as androidx.compose.ui.platform.AbstractComposeView
        val target = ComposeMaskTarget.from(composeView, logger)
        if (target != null) {
            traverseComposeNodes(target, inherited, context, masks)
        }

        for (i in 0 until composeView.childCount) {
            val child = composeView.getChildAt(i)
            traverse(child, inherited, context, masks)
        }
    }

    /**
     * Visits a plain native View: applies the precedence rules to decide whether to emit a mask
     * for this view, then recurses into its children passing the resolved-explicit state.
     *
     * @param view the native view to evaluate.
     * @param inherited see [traverse].
     * @param context see [traverse].
     * @param masks see [traverse].
     */
    private fun traverseNative(view: View, inherited: Boolean?, context: MaskContext, masks: MutableList<Mask>) {
        val target = NativeMaskTarget(view)
        val resolvedExplicit = resolveExplicit(target, inherited, context)
        if (shouldMask(target, resolvedExplicit, context)) {
            target.mask(context)?.let { masks += it }
        }

        if (view !is ViewGroup) return

        for (i in 0 until view.childCount) {
            val child = view.getChildAt(i)
            traverse(child, resolvedExplicit, context, masks)
        }
    }

    /**
     * Visits a Compose semantics node and its descendants, applying the same precedence rules
     * used for native Views. Compose nodes carry their own explicit masking signal via the
     * `LdMaskSemanticsKey` semantics property, exposed through [MaskTarget.hasLDMask] /
     * [MaskTarget.hasLDUnmask].
     *
     * @param target compose target wrapping the node being visited.
     * @param inherited see [traverse].
     * @param context see [traverse].
     * @param masks see [traverse].
     */
    private fun traverseComposeNodes(
        target: ComposeMaskTarget,
        inherited: Boolean?,
        context: MaskContext,
        masks: MutableList<Mask>
    ) {
        val resolvedExplicit = resolveExplicit(target, inherited, context)
        if (shouldMask(target, resolvedExplicit, context)) {
            target.mask(context)?.let { masks += it }
        }

        for (child in target.rootNode.children) {
            val childTarget = ComposeMaskTarget(
                view = target.view,
                rootNode = child,
                config = child.config,
                boundsInWindow = child.boundsInWindow
            )
            traverseComposeNodes(childTarget, resolvedExplicit, context, masks)
        }
    }

    /**
     * Combines the target's own explicit signal with [inherited] from ancestors and returns the
     * resolved-explicit state for this target. The result is what gets propagated to descendants
     * — global-matcher matches are deliberately not part of this state.
     *
     * @param target the target whose explicit state we're resolving.
     * @param inherited resolved-explicit state from the nearest ancestor.
     * @param context provides the [MaskContext.explicitMaskMatchers] consulted during resolution.
     * @return `true` for resolved mask, `false` for resolved unmask, `null` when no explicit
     *     signal applies.
     */
    private fun resolveExplicit(target: MaskTarget, inherited: Boolean?, context: MaskContext): Boolean? {
        val self = explicitOf(target, context)
        return when {
            self == true || inherited == true -> true
            self == false || inherited == false -> false
            else -> null
        }
    }

    /**
     * The target's *own* explicit signal, ignoring ancestors. Per-view markers
     * ([MaskTarget.hasLDMask] / [MaskTarget.hasLDUnmask]) and any
     * [MaskContext.explicitMaskMatchers] entry that matches all count as explicit signals; mask
     * wins over unmask if both are present on the same target.
     *
     * @param target the target to inspect.
     * @param context provides [MaskContext.explicitMaskMatchers].
     * @return `true` for explicit mask, `false` for explicit unmask, `null` for no signal.
     */
    private fun explicitOf(target: MaskTarget, context: MaskContext): Boolean? = when {
        target.hasLDMask() -> true
        context.explicitMaskMatchers.any { it.isMatch(target) } -> true
        target.hasLDUnmask() -> false
        else -> null
    }

    /**
     * Decides whether to emit a mask for [target] given its [resolvedExplicit] state. Falls
     * through to [MaskContext.globalMaskMatchers] only when no explicit signal applies to the
     * target or any of its ancestors.
     *
     * @param target the target being evaluated.
     * @param resolvedExplicit value returned by [resolveExplicit].
     * @param context provides [MaskContext.globalMaskMatchers].
     */
    private fun shouldMask(target: MaskTarget, resolvedExplicit: Boolean?, context: MaskContext): Boolean {
        return resolvedExplicit ?: context.globalMaskMatchers.any { it.isMatch(target) }
    }

    /**
     * Whether [view] is the internal `AndroidComposeView` host that wraps a Compose subtree.
     * Detected by class-name suffix because the type isn't part of the public Compose API.
     *
     * @param view the view to inspect.
     */
    private fun isAndroidComposeView(view: View): Boolean {
        return view::class.java.name.contains("AndroidComposeView")
    }

    /**
     * Visits an `AndroidComposeView`, which holds Compose content but isn't itself a target we
     * evaluate. Recurses into its children carrying [inherited] forward.
     *
     * @param view the AndroidComposeView; recursion only proceeds if it's a [ViewGroup].
     * @param inherited see [traverse].
     * @param context see [traverse].
     * @param masks see [traverse].
     */
    private fun traverseAndroidComposeView(
        view: View,
        inherited: Boolean?,
        context: MaskContext,
        masks: MutableList<Mask>
    ) {
        if (view !is ViewGroup) return

        for (i in 0 until view.childCount) {
            val child = view.getChildAt(i)
            traverse(child, inherited, context, masks)
        }
    }
}
