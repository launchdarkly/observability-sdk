package com.launchdarkly.observability.replay.masking

import android.view.View
import androidx.compose.ui.semantics.SemanticsConfiguration
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull

/**
 * Compose target with a non-null [SemanticsConfiguration].
 */
data class ComposeMaskTarget(
    override val view: View,
    val config: SemanticsConfiguration,
) : MaskTarget {
    override fun isTextInput(): Boolean {
        return config.contains(SemanticsProperties.EditableText) ||
                config.contains(SemanticsActions.SetText) ||
                config.contains(SemanticsActions.PasteText) ||
                config.contains(SemanticsActions.InsertTextAtCursor)
    }

    override fun isText(): Boolean {
        return config.contains(SemanticsProperties.Text)
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


