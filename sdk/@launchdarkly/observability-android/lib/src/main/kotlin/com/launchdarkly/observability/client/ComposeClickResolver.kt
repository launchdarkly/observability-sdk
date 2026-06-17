package com.launchdarkly.observability.client

import android.view.View
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsOwner
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import com.launchdarkly.observability.api.LdIdSemanticsKey

/**
 * Resolved description of the deepest Compose element under a touch point.
 *
 * @property ldId the developer-supplied [LdIdSemanticsKey] for the deepest node carrying one
 *   (falling back to the deepest node's test tag), or null when none is set.
 * @property text a privacy-safe label (visible text or content description), or null.
 * @property role the element's semantics role (e.g. `"Button"`), or null.
 */
internal data class ComposeClickInfo(
    val ldId: String?,
    val text: String?,
    val role: String?,
)

/**
 * Hit-tests a Jetpack Compose host's semantics tree to describe the element under a touch point.
 *
 * Compose renders its content into a single `AndroidComposeView`, so the native [View] hit-test in
 * [UserInteractionManager] bottoms out at that host and can't see individual composables. This
 * resolver walks the host's semantics tree (the same source masking uses) to find the deepest node
 * containing the point and reads its analytics identifier, label and role.
 *
 * All Compose symbols are confined to this file so the JVM verifier only loads them when Compose UI
 * is actually on the classpath. Callers must guard invocations behind a class-name check (see
 * [UserInteractionManager.isAndroidComposeView]) so this object is never loaded in non-Compose apps.
 */
internal object ComposeClickResolver {

    private val androidComposeViewClass: Class<*>? by lazy {
        try {
            Class.forName("androidx.compose.ui.platform.AndroidComposeView")
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Resolves the deepest Compose element under ([windowX], [windowY]) within [view], an
     * `AndroidComposeView`. Coordinates are window-relative, matching Compose's
     * [SemanticsNode.boundsInWindow]. Best-effort: any failure (or no node at the point) returns
     * null so the caller can fall back to native resolution.
     */
    fun resolve(view: View, windowX: Float, windowY: Float): ComposeClickInfo? {
        val root = rootSemanticsNode(view) ?: return null

        var ldId: String? = null
        var deepest: SemanticsNode? = null

        fun visit(node: SemanticsNode) {
            val bounds = node.boundsInWindow
            if (windowX < bounds.left || windowX >= bounds.right ||
                windowY < bounds.top || windowY >= bounds.bottom
            ) {
                return
            }
            deepest = node
            node.config.getOrNull(LdIdSemanticsKey)?.takeIf { it.isNotEmpty() }?.let { ldId = it }
            for (child in node.children) {
                visit(child)
            }
        }
        visit(root)

        val node = deepest ?: return null
        val resolvedId = ldId
            ?: node.config.getOrNull(SemanticsProperties.TestTag)?.takeIf { it.isNotEmpty() }
        return ComposeClickInfo(
            ldId = resolvedId,
            text = nodeText(node),
            role = node.config.getOrNull(SemanticsProperties.Role)?.toString(),
        )
    }

    /**
     * Reads the unmerged root semantics node from an `AndroidComposeView` via reflection
     * (`semanticsOwner` is not public API), or null when unavailable.
     */
    private fun rootSemanticsNode(view: View): SemanticsNode? {
        val cls = androidComposeViewClass ?: return null
        if (!cls.isInstance(view)) return null
        return try {
            val field = cls.getDeclaredField("semanticsOwner").apply { isAccessible = true }
            (field.get(view) as? SemanticsOwner)?.unmergedRootSemanticsNode
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Privacy-safe label for a node: never returns text from password fields. Prefers visible text,
     * then content description.
     */
    private fun nodeText(node: SemanticsNode): String? {
        if (node.config.contains(SemanticsProperties.Password)) return null
        node.config.getOrNull(SemanticsProperties.Text)
            ?.joinToString(separator = " ") { it.text }
            ?.takeIf { it.isNotEmpty() }
            ?.let { return it }
        return node.config.getOrNull(SemanticsProperties.ContentDescription)
            ?.firstOrNull()
            ?.takeIf { it.isNotEmpty() }
    }
}
