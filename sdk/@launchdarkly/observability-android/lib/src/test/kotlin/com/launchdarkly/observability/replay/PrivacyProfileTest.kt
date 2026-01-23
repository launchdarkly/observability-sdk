package com.launchdarkly.observability.replay

import android.widget.ImageView
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PrivacyProfileTest {

    @Test
    fun `maskViews defaults to empty list`() {
        val profile = PrivacyProfile()
        assertTrue(profile.maskViews.isEmpty())
    }

    @Test
    fun `maskImageViews defaults to false`() {
        val profile = PrivacyProfile()
        assertFalse(profile.maskImageViews)
    }

    @Test
    fun `maskXMLViewIds defaults to empty list`() {
        val profile = PrivacyProfile()
        assertTrue(profile.maskXMLViewIds.isEmpty())
    }

    @Test
    fun `maskWebViews defaults to false`() {
        val profile = PrivacyProfile()
        assertFalse(profile.maskWebViews)
    }

    @Test
    fun `maskViews populates viewClassSet and adds viewsMatcher to matchers list`() {
        val maskedClass = FakeMaskedView::class.java
        val profile = PrivacyProfile(maskViews = listOf(view(maskedClass)))

        val matchers = profile.asMatchersList()
        assertTrue(matchers.contains(profile.viewsMatcher))

        val viewClassSet = profile.getPrivateSet("viewClassSet")
        assertTrue(viewClassSet.contains(maskedClass))
    }

    @Test
    fun `maskXMLViewIds normalizes @+id and @id prefixes and adds xmlViewIdsMatcher to matchers list`() {
        val profile = PrivacyProfile(maskXMLViewIds = listOf("@+id/foo", "@id/baz", "bar"))

        val matchers = profile.asMatchersList()
        assertTrue(matchers.contains(profile.xmlViewIdsMatcher))

        val idSet = profile.getPrivateSet("maskXMLViewIdSet")
        assertTrue(idSet.contains("foo"))
        assertTrue(idSet.contains("baz"))
        assertTrue(idSet.contains("bar"))
        assertFalse(idSet.contains("@+id/foo"))
        assertFalse(idSet.contains("@id/baz"))
    }

    @Test
    fun `maskImageViews adds ImageView to viewClassSet and includes viewsMatcher even when maskViews is empty`() {
        val profile = PrivacyProfile(maskImageViews = true, maskViews = emptyList())

        val matchers = profile.asMatchersList()
        assertTrue(matchers.contains(profile.viewsMatcher))

        val viewClassSet = profile.getPrivateSet("viewClassSet")
        assertTrue(viewClassSet.contains(ImageView::class.java))
    }

    @Test
    fun `maskImageViews false does not add ImageView to viewClassSet and does not include viewsMatcher when maskViews is empty`() {
        val profile = PrivacyProfile(maskImageViews = false, maskViews = emptyList())

        val matchers = profile.asMatchersList()
        assertFalse(matchers.contains(profile.viewsMatcher))

        val viewClassSet = profile.getPrivateSet("viewClassSet")
        assertFalse(viewClassSet.contains(ImageView::class.java))
    }

    @Test
    fun `maskWebViews adds default WebView class names to class name matcher set`() {
        val profile = PrivacyProfile(maskWebViews = true)

        val matchers = profile.asMatchersList()
        assertTrue(matchers.contains(profile.webViewClassHierarchyMatcher))

        val classNameSet = profile.getPrivateSet("webViewClassNameSet")
        assertTrue(
            classNameSet.containsAll(
                listOf(
                    "android.webkit.WebView",
                    "org.mozilla.geckoview.GeckoView",
                    "org.xwalk.core.XWalkView",
                    "com.tencent.smtt.sdk.WebView",
                    "com.uc.webview.export.WebView",
                )
            )
        )
    }

    @Test
    fun `invalid string-based maskViews class name throws targeted error`() {
        val fqcn = "com.example.this.does.not.ExistView"
        val ex = assertThrows(IllegalArgumentException::class.java) {
            PrivacyProfile(maskViews = listOf(view(fqcn)))
        }

        val message = ex.message ?: ""
        assertTrue(message.contains("PrivacyProfile.maskViews"))
        assertTrue(message.contains(fqcn))
        assertTrue(ex.cause is ClassNotFoundException)
    }

    private fun PrivacyProfile.getPrivateSet(fieldName: String): Set<Any?> {
        val field = PrivacyProfile::class.java.getDeclaredField(fieldName)
        field.isAccessible = true
        val value = field.get(this)
        @Suppress("UNCHECKED_CAST")
        return value as Set<Any?>
    }

    private class FakeMaskedView
}
