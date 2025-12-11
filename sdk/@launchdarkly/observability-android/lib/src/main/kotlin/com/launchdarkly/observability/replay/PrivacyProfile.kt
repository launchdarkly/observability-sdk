package com.launchdarkly.observability.replay

import com.launchdarkly.observability.replay.masking.MaskMatcher
import com.launchdarkly.observability.replay.masking.MaskTarget

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
                return target.isTextInput()
            }
        }

        /**
         * This matcher will match most text, but there may be special cases where it will
         * miss as we can't account for all possible future semantic properties.
         */
        val textMatcher: MaskMatcher = object : MaskMatcher {
            override fun isMatch(target: MaskTarget): Boolean {
                return target.isText()
            }
        }

        /**
         * This matcher will match all items having the semantic property [SemanticsProperties.Password]
         * and all text or context descriptions that have substring matches with any of the [sensitiveKeywords]
         */
        val sensitiveMatcher: MaskMatcher = object : MaskMatcher {
            override fun isMatch(target: MaskTarget): Boolean {
                return target.isSensitive(sensitiveKeywords)
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
