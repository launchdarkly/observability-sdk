package com.launchdarkly.observability.replay

import android.widget.EditText
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import com.launchdarkly.observability.replay.masking.ComposeMaskTarget
import com.launchdarkly.observability.replay.masking.MaskMatcher
import com.launchdarkly.observability.replay.masking.MaskTarget
import com.launchdarkly.observability.replay.masking.NativeMaskTarget

/**
 * [PrivacyProfile] encapsulates options and functionality related to privacy of session
 * replay functionality.
 *
 * By default, session replay will apply an opaque mask to text inputs, text, and sensitive views.
 * See [sensitiveMatcher] for specific details.
 *
 * @param maskTextInputs set to false to turn off masking text inputs
 * @param maskText set to false to turn off masking text
 * @param maskSensitive set to false to turn off masking sensitive views
 * @param maskAdditionalMatchers list of additional [com.launchdarkly.observability.replay.masking.MaskMatcher]s that will be masked when they match
 **/
data class PrivacyProfile(
    val maskTextInputs: Boolean = true,
    val maskText: Boolean = true,
    val maskSensitive: Boolean = true,
    val maskAdditionalMatchers: List<MaskMatcher> = emptyList(),
) {

    /**
     * Converts this [PrivacyProfile] into its equivalent [MaskMatcher] list.
     */
    internal fun asMatchersList(): List<MaskMatcher> = buildList {
        if (maskTextInputs) add(textInputMatcher)
        if (maskText) add(textMatcher)
        if (maskSensitive) add(sensitiveMatcher)
        addAll(maskAdditionalMatchers)
    }

    companion object {
        /**
         * This matcher will match most text inputs, but there may be special cases where it will
         * miss as we can't account for all possible future semantic properties.
         */
        val textInputMatcher: MaskMatcher = object : MaskMatcher {
            override fun isMatch(target: MaskTarget): Boolean {
                return when (target) {
                    is ComposeMaskTarget -> {
                        val config = target.config
                        config.contains(SemanticsProperties.EditableText) ||
                                config.contains(SemanticsActions.SetText) ||
                                config.contains(SemanticsActions.PasteText) ||
                                config.contains(SemanticsActions.InsertTextAtCursor)
                    }
                    is NativeMaskTarget -> {
                        target.view is EditText
                    }
                    else -> false
                }
            }
        }

        /**
         * This matcher will match most text, but there may be special cases where it will
         * miss as we can't account for all possible future semantic properties.
         */
        val textMatcher: MaskMatcher = object : MaskMatcher {
            override fun isMatch(target: MaskTarget): Boolean {
                return when (target) {
                    is ComposeMaskTarget -> target.config.contains(SemanticsProperties.Text)
                    else -> false
                }
            }
        }

        /**
         * This matcher will match all items having the semantic property [SemanticsProperties.Password]
         * and all text or context descriptions that have substring matches with any of the [sensitiveKeywords]
         */
        val sensitiveMatcher: MaskMatcher = object : MaskMatcher {
            override fun isMatch(target: MaskTarget): Boolean {/**/
                val config = (target as? ComposeMaskTarget)?.config ?: return false
                if (config.contains(SemanticsProperties.Password)) {
                    return true
                }

                // check text first for performance, more likely to get a match here than in description below
                val textValues = config.getOrNull(SemanticsProperties.Text)
                if (textValues != null) {
                    if (textValues.any { annotated ->
                            val lowerText = annotated.text.lowercase()
                            sensitiveKeywords.any { keyword ->
                                // could use ignoreCase = true here, but that is less
                                // performant than lower casing desc once above
                                lowerText.contains(keyword)
                            }
                        }) return true
                }

                // check content description
                val contentDescriptions =
                    config.getOrNull(SemanticsProperties.ContentDescription)
                if (contentDescriptions != null) {
                    if (contentDescriptions.any { desc ->
                            val lowerDesc = desc.lowercase()
                            sensitiveKeywords.any { keyword ->
                                // could use ignoreCase = true here, but that is less
                                // performant than lower casing desc once above
                                lowerDesc.contains(keyword)
                            }
                        }) return true
                }

                return false
            }
        }

        // this list of sensitive keywords is used to detect sensitive content descriptions
        private val sensitiveKeywords = listOf(
            "sensitive",
            "private",
            "name",
            "email",
            "username",
            "cell",
            "mobile",
            "phone",
            "address",
            "street",
            "dob",
            "birth",
            "password",
            "account",
            "ssn",
            "social",
            "security",
            "credit",
            "debit",
            "card",
            "cvv",
            "mm/yy",
            "pin",
        )
    }
}
