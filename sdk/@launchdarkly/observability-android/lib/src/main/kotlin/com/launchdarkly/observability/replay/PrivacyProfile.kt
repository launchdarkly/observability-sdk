package com.launchdarkly.observability.replay

import android.view.View
import android.widget.ImageView
import com.launchdarkly.observability.replay.masking.MaskMatcher
import com.launchdarkly.observability.replay.masking.MaskTarget

/**
 * [PrivacyProfile] controls what UI elements are masked in session replay.
 *
 * Masking is implemented as a list of [MaskMatcher]s that are evaluated against a [MaskTarget].
 * Targets can represent native Android Views as well as Jetpack Compose semantics nodes.
 *
 *
 * @param maskTextInputs Set to false to disable masking text input targets.
 * @param maskText Set to false to disable masking text targets.
 * @param maskImageViews Set to true to mask [ImageView] targets by exact class match.
 * @param maskViews Additional Views to mask by exact class match (see [viewsMatcher]).
 * @param maskXMLViewIds Additional Views to mask by resource entry name (see [xmlViewIdsMatcher]).
 * @param maskBySemanticsKeywords Set to true to enable masking of "sensitive" targets detected by
 * semantic keywords (password + keyword heuristics).
 * Accepts either `"@+id/foo"` or `"foo"`.
 **/
data class PrivacyProfile(
    val maskTextInputs: Boolean = true,
    val maskText: Boolean = false,
    val maskViews: List<MaskViewRef> = emptyList(),
    val maskXMLViewIds: List<String> = emptyList(),
    // only for XML ImageViews
    val maskImageViews: Boolean = false,
    val maskBySemanticsKeywords: Boolean = false,
    ) {
    private val viewClassSet = buildSet {
        addAll(maskViews.map { it.clazz })
        if (maskImageViews) add(ImageView::class.java)
    }

    private val maskXMLViewIdSet = maskXMLViewIds.map {
        if (it.startsWith("@+id/")) return@map it.substring(5)
        return@map it
    }.toSet()

    /**
     * Converts this [PrivacyProfile] into its equivalent [MaskMatcher] list.
     *
     * Note: matchers are evaluated with `any { ... }`, so ordering only affects performance
     * (earlier matchers can short-circuit later ones).
     */
    internal fun asMatchersList(): List<MaskMatcher> = buildList {
        // Prefer cheaper checks first; heavier checks should be later.
        if (maskTextInputs) add(textInputMatcher)
        if (maskText) add(textMatcher)
        if (viewClassSet.isNotEmpty()) add(viewsMatcher)
        if (maskXMLViewIdSet.isNotEmpty()) add(xmlViewIdsMatcher)
        if (maskBySemanticsKeywords) add(sensitiveMatcher)
    }

    /**
     * Matches targets whose underlying Android View has an exact class match with [maskViews].
     *
     * Note: this uses `target.view.javaClass` equality; it does not match subclasses.
     */
    val viewsMatcher: MaskMatcher = object : MaskMatcher {
        override fun isMatch(target: MaskTarget): Boolean {
            return viewClassSet.contains(target.view.javaClass)
        }
    }

    /**
     * Matches targets whose underlying Android View's resource entry name is included in
     * [maskXMLViewIds].
     *
     * IDs are compared using `resources.getResourceEntryName(view.id)`, so this only applies to
     * Views with a non-[View.NO_ID] id that resolves to a resource entry.
     */
    val xmlViewIdsMatcher: MaskMatcher = object : MaskMatcher {
        fun View.idNameOrNull(): String? =
            if (id == View.NO_ID) null
            else runCatching { resources.getResourceEntryName(id) }.getOrNull()

        override fun isMatch(target: MaskTarget): Boolean {
            val id = target.view.idNameOrNull() ?: return false

            return maskXMLViewIdSet.contains(id)
        }
    }

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
     * This matcher will match all items having the semantic property
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

