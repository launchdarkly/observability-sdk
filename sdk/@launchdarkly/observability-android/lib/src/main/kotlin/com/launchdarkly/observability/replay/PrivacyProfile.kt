import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import com.launchdarkly.observability.replay.MaskMatcher

data class PrivacyProfile private constructor(
    internal val maskMatchers: List<MaskMatcher>,
) {
    companion object {

        val textInputMatcher: MaskMatcher = object : MaskMatcher {
            override fun isMatch(node: SemanticsNode): Boolean {
                return node.config.contains(SemanticsProperties.EditableText) ||
                        node.config.contains(SemanticsActions.SetText) ||
                        node.config.contains(SemanticsActions.PasteText) ||
                        node.config.contains(SemanticsActions.InsertTextAtCursor)
            }
        }

        val textMatcher: MaskMatcher = object : MaskMatcher {
            override fun isMatch(node: SemanticsNode): Boolean {
                return node.config.contains(SemanticsProperties.Text)
            }
        }

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

        // TODO: make sensitiveMatcher last as it is the most expensive and cheaper options short circuiting early is better
        val builtInMatchers = listOf(textInputMatcher, textMatcher, sensitiveMatcher)

        fun noMasking() = PrivacyProfile(
            maskMatchers = emptyList(),
        )

        fun strict(additionalMaskMatchers: List<MaskMatcher> = emptyList()) = PrivacyProfile(
            maskMatchers = buildList {
                addAll(builtInMatchers)
                addAll(additionalMaskMatchers)
            },
        )

        fun optIn(maskMatchers: List<MaskMatcher>) = PrivacyProfile(
            maskMatchers = maskMatchers,
        )

        fun optOut(unmaskMatchers: List<MaskMatcher>) = PrivacyProfile(
            maskMatchers = builtInMatchers.filter { it !in unmaskMatchers },
        )

        val sensitiveKeywords = listOf(
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
            "birthdate",
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
