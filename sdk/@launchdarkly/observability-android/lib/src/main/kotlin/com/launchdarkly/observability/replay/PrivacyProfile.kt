package com.launchdarkly.observability.replay

import android.view.View
import android.webkit.WebView
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
 * accepts `"@+id/foo"`, `"@id/foo"`, or `"foo"`.
 * @param maskWebViews Set to true to mask known WebView types and their subclasses
 * (e.g., "android.webkit.WebView", "org.mozilla.geckoview.GeckoView", etc).
 * @param maskBySemanticsKeywords Set to true to enable masking of "sensitive" targets detected by
 * semantic keywords (password + keyword heuristics).
 **/
data class PrivacyProfile(
    val maskTextInputs: Boolean = true,
    val maskText: Boolean = false,
    val maskViews: List<MaskViewRef> = emptyList(),
    val maskXMLViewIds: List<String> = emptyList(),
    // only for XML ImageViews
    val maskImageViews: Boolean = false,
    val maskWebViews: Boolean = false,
    val maskBySemanticsKeywords: Boolean = false,
) {
    private val viewClassSet = buildSet {
        addAll(maskViews.map { it.clazz })
        if (maskImageViews) add(ImageView::class.java)
        if (maskWebViews) add(WebView::class.java)
    }

    private val webViewClassNameSet = if (maskWebViews) webViewClassNames.toSet() else emptySet()

    private val maskXMLViewIdSet = maskXMLViewIds.map {
        when {
            it.startsWith("@+id/") -> it.substring(5)
            it.startsWith("@id/") -> it.substring(4)
            else -> it
        }
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
        if (webViewClassNameSet.isNotEmpty()) add(webViewClassHierarchyMatcher)
    }

    /**
     * Matches targets whose underlying Android View has an exact class match with [maskViews].
     *
     * Note: this uses `target.view.javaClass` equality; it does not match subclasses.
     */
    internal val viewsMatcher: MaskMatcher = object : MaskMatcher {
        override fun isMatch(target: MaskTarget): Boolean {
            return viewClassSet.contains(target.view.javaClass)
        }
    }

    /**
     * Matches targets whose underlying Android View is a subclass (or implements an interface)
     * with a class name included in [webViewClassNames] when [maskWebViews] is enabled.
     */
    internal val webViewClassHierarchyMatcher: MaskMatcher = object : MaskMatcher {
        private val cache = HashMap<Class<*>, Boolean>()

        override fun isMatch(target: MaskTarget): Boolean {
            val viewClass = target.view.javaClass
            return cache.getOrPut(viewClass) {
                matchesClassHierarchy(viewClass, webViewClassNameSet)
            }
        }
    }

    /**
     * Matches targets whose underlying Android View's resource entry name is included in
     * [maskXMLViewIds].
     *
     * IDs are compared using `resources.getResourceEntryName(view.id)`, so this only applies to
     * Views with a non-[View.NO_ID] id that resolves to a resource entry.
     */
    internal val xmlViewIdsMatcher: MaskMatcher = object : MaskMatcher {
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
    internal val textInputMatcher: MaskMatcher = object : MaskMatcher {
        override fun isMatch(target: MaskTarget): Boolean {
            return target.isTextInput()
        }
    }

    /**
     * This matcher will match most text, but there may be special cases where it will
     * miss as we can't account for all possible future semantic properties.
     */
    internal val textMatcher: MaskMatcher = object : MaskMatcher {
        override fun isMatch(target: MaskTarget): Boolean {
            return target.isText()
        }
    }

    /**
     * This matcher will match all items having the semantic property
     * and all text or context descriptions that have substring matches with any of the [sensitiveKeywords]
     */
    internal val sensitiveMatcher: MaskMatcher = object : MaskMatcher {
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

    private fun matchesClassHierarchy(
        viewClass: Class<*>,
        classNameSet: Set<String>
    ): Boolean {
        if (classNameSet.isEmpty()) return false

        val seenInterfaces = HashSet<Class<*>>()
        var current: Class<*>? = viewClass
        while (current != null) {
            if (classNameSet.contains(current.name)) return true
            if (hasMatchingInterface(current, seenInterfaces, classNameSet)) return true
            current = current.superclass
        }
        return false
    }

    private fun hasMatchingInterface(
        clazz: Class<*>,
        seen: HashSet<Class<*>>,
        classNameSet: Set<String>
    ): Boolean {
        val queue = ArrayDeque<Class<*>>()
        queue.addAll(clazz.interfaces)
        while (queue.isNotEmpty()) {
            val interfaceClass = queue.removeFirst()
            if (!seen.add(interfaceClass)) continue
            if (classNameSet.contains(interfaceClass.name)) return true
            queue.addAll(interfaceClass.interfaces)
        }
        return false
    }

    companion object {
        private val webViewClassNames = listOf(
            WebView::class.java.name,
            "org.mozilla.geckoview.GeckoView",
            "org.xwalk.core.XWalkView",
            "com.tencent.smtt.sdk.WebView",
            "com.uc.webview.export.WebView",
        )
    }
}
