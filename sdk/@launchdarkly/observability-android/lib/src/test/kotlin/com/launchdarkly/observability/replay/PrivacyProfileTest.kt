package com.launchdarkly.observability.replay

import android.widget.ImageView
import org.junit.jupiter.api.Assertions.assertFalse
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
    fun `maskViews populates viewClassSet and adds viewsMatcher to matchers list`() {
        val maskedClass = FakeMaskedView::class.java
        val profile = PrivacyProfile(maskViews = listOf(view(maskedClass)))

        val matchers = profile.asMatchersList()
        assertTrue(matchers.contains(profile.viewsMatcher))

        val viewClassSet = profile.getPrivateSet("viewClassSet")
        assertTrue(viewClassSet.contains(maskedClass))
    }

    @Test
    fun `maskXMLViewIds normalizes @+id prefix and adds xmlViewIdsMatcher to matchers list`() {
        val profile = PrivacyProfile(maskXMLViewIds = listOf("@+id/foo", "bar"))

        val matchers = profile.asMatchersList()
        assertTrue(matchers.contains(profile.xmlViewIdsMatcher))

        val idSet = profile.getPrivateSet("maskXMLViewIdSet")
        assertTrue(idSet.contains("foo"))
        assertTrue(idSet.contains("bar"))
        assertFalse(idSet.contains("@+id/foo"))
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

    private fun PrivacyProfile.getPrivateSet(fieldName: String): Set<Any?> {
        val field = PrivacyProfile::class.java.getDeclaredField(fieldName)
        field.isAccessible = true
        val value = field.get(this)
        @Suppress("UNCHECKED_CAST")
        return value as Set<Any?>
    }

    private class FakeMaskedView
}


