package com.launchdarkly.observability.replay.masking

import android.view.View
import androidx.compose.ui.graphics.toAndroidRectF
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsOwner
import androidx.compose.ui.semantics.SemanticsConfiguration
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import com.launchdarkly.observability.api.LdMaskSemanticsKey
import androidx.compose.ui.geometry.Rect as MaskRect
import androidx.core.view.isNotEmpty
import com.launchdarkly.logging.LDLogger

/**
 * Compose target with a non-null [SemanticsConfiguration].
 */
data class ComposeMaskTarget(
    override val view: View,
    val rootNode: SemanticsNode,
    val config: SemanticsConfiguration,
    val boundsInWindow: MaskRect,
) : MaskTarget {
    companion object {
        fun from(composeView: ComposeView, logger: LDLogger): ComposeMaskTarget? {
            val root = getRootSemanticsNode(composeView, logger) ?: return null
            return ComposeMaskTarget(
                view = composeView,
                rootNode = root,
                config = root.config,
                boundsInWindow = root.boundsInWindow
            )
        }

        /**
         * Gets the SemanticsOwner from a ComposeView using reflection. This is necessary because
         * AndroidComposeView and semanticsOwner are not publicly exposed.
         */
        private fun getRootSemanticsNode(composeView: ComposeView, logger: LDLogger): SemanticsNode? {
            return try {
                if (composeView.isNotEmpty()) {
                    val androidComposeView = composeView.getChildAt(0)
                    val androidComposeViewClass =
                        Class.forName("androidx.compose.ui.platform.AndroidComposeView")
                    if (androidComposeViewClass.isInstance(androidComposeView)) {
                        val field = androidComposeViewClass.getDeclaredField("semanticsOwner")
                        field.isAccessible = true
                        val owner = field.get(androidComposeView) as? SemanticsOwner
                        owner?.unmergedRootSemanticsNode
                    } else {
                        null
                    }
                } else {
                    null
                }
            } catch (err: Exception) {
                logger.error("Failed to get Root Semantics Node: ${err.message}")
                null
            }
        }
    }

    override fun isTextInput(): Boolean {
        return config.contains(SemanticsProperties.EditableText) ||
                config.contains(SemanticsActions.SetText) ||
                config.contains(SemanticsActions.PasteText) ||
                config.contains(SemanticsActions.InsertTextAtCursor)
    }

    override fun isText(): Boolean {
        return config.contains(SemanticsProperties.Text)
    }

    override fun mask(): Mask? {
        return Mask(boundsInWindow.toAndroidRectF(), view.id)
    }

    override fun hasLDMask(): Boolean {
        return config.getOrNull(LdMaskSemanticsKey) == true
    }

    override fun isSensitive(sensitiveKeywords: List<String>): Boolean {
        if (config.contains(SemanticsProperties.Password)) return true

        val hasSensitiveText = config.getOrNull(SemanticsProperties.Text)?.any { annotated ->
            val lowerText = annotated.text.lowercase()
            sensitiveKeywords.any { keyword -> lowerText.contains(keyword) }
        } == true
        if (hasSensitiveText) return true

        val hasSensitiveDescription =
            config.getOrNull(SemanticsProperties.ContentDescription)?.any { desc ->
                val lowerDesc = desc.lowercase()
                sensitiveKeywords.any { keyword -> lowerDesc.contains(keyword) }
            } == true

        return hasSensitiveDescription
    }

}


