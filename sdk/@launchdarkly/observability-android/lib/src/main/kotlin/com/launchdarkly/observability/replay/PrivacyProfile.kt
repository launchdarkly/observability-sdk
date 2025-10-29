import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import com.launchdarkly.observability.replay.MaskMatcher

/**
 * The [PrivacyProfile] class encapsulates options and functionality related to privacy of session
 * replay functionality.
 *
 * By default, session replay will apply an opaque mask to elemetns that match the [builtInMatchers].
 * To customize this behavior, use one of [strict], [optIn], [optOut]
 **/
data class PrivacyProfile private constructor(
    internal val maskMatchers: List<MaskMatcher>,
) {
    companion object {

        /**
         * This matcher will match most text inputs, but there may be special cases where it will
         * miss as we can't account for all possible future semantic properties.
         */
        val textInputMatcher: MaskMatcher = object : MaskMatcher {
            override fun isMatch(node: SemanticsNode): Boolean {
                val config = node.config;
                return config.contains(SemanticsProperties.EditableText) ||
                        config.contains(SemanticsActions.SetText) ||
                        config.contains(SemanticsActions.PasteText) ||
                        config.contains(SemanticsActions.InsertTextAtCursor)
            }
        }

        /**
         * This matcher will match most text, but there may be special cases where it will
         * miss as we can't account for all possible future semantic properties.
         */
        val textMatcher: MaskMatcher = object : MaskMatcher {
            override fun isMatch(node: SemanticsNode): Boolean {
                return node.config.contains(SemanticsProperties.Text)
            }
        }

        /**
         * This matcher will match all items having the semantic property [SemanticsProperties.Password]
         * and all text or context descriptions that have substring matches with any of the [sensitiveKeywords]
         */
        val sensitiveMatcher: MaskMatcher = object : MaskMatcher {
            override fun isMatch(node: SemanticsNode): Boolean {/**/
                if (node.config.contains(SemanticsProperties.Password)) {
                    return true
                }

                // check text first for performance, more likely to get a match here than in description below
                val textValues = node.config.getOrNull(SemanticsProperties.Text)
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
                    node.config.getOrNull(SemanticsProperties.ContentDescription)
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

        /**
         * The list of built in matchers.
         */
        val builtInMatchers = listOf(textInputMatcher, textMatcher, sensitiveMatcher)

        /**
         * A [PrivacyProfile] that will perform no masking.
         *
         * @sample
         * ```kotlin
         * ReplayInstrumentation(
         *     options = ReplayOptions(
         *         privacyProfile = PrivacyProfile.noMasking()
         *     )
         * )
         * ```
         */
        fun noMasking() = PrivacyProfile(
            maskMatchers = emptyList(),
        )

        /**
         * A [PrivacyProfile] that uses [builtInMatchers] plus any additional [MaskMatcher]s provided.
         *
         * Matchers should not do heavy work, should execute synchronously, and not dispatch to other
         * threads for performance reasons.  If you add a matcher and notice jitter, this may be
         * the cause.
         *
         * @sample
         * ```kotlin
         * ReplayInstrumentation(
         *     options = ReplayOptions(
         *         privacyProfile = PrivacyProfile.optIn(listOf(PrivacyProfile.sensitiveMatcher))
         *     )
         * )
         * ```
         *
         * @param additionalMatchers additional matchers that will also run after built in matchers
         */
        fun strict(additionalMatchers: List<MaskMatcher> = emptyList()) = PrivacyProfile(
            maskMatchers = buildList {
                addAll(builtInMatchers)
                addAll(additionalMatchers)
            },
        )

        /**
         * A [PrivacyProfile] that uses only the provided [MaskMatcher]s
         *
         * @sample
         * ```kotlin
         * ReplayInstrumentation(
         *     options = ReplayOptions(
         *         privacyProfile = PrivacyProfile.optIn(listOf(PrivacyProfile.sensitiveMatcher))
         *     )
         * )
         * ```
         *
         * @param matchers the matchers to use
         */
        fun optIn(matchers: List<MaskMatcher>) = PrivacyProfile(
            maskMatchers = matchers,
        )

        /**
         * A [PrivacyProfile] that uses all [builtInMatchers] except those provided.
         *
         * @sample
         * ```kotlin
         * ReplayInstrumentation(
         *     options = ReplayOptions(
         *         privacyProfile = PrivacyProfile.optOut(listOf(PrivacyProfile.textMatcher))
         *     )
         * )
         * ```
         *
         * @param matchers the matchers to NOT use
         */
        fun optOut(matchers: List<MaskMatcher>) = PrivacyProfile(
            maskMatchers = builtInMatchers.filter { it !in matchers },
        )

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
