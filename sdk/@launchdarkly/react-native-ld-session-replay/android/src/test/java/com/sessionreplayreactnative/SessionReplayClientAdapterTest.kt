package com.sessionreplayreactnative

import android.app.Application
import com.facebook.react.bridge.ReadableMap
import com.launchdarkly.sdk.ContextKind
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SessionReplayClientAdapterTest {

    private fun newAdapter(): SessionReplayClientAdapter {
        val constructor = SessionReplayClientAdapter::class.java.getDeclaredConstructor()
        constructor.isAccessible = true
        return constructor.newInstance()
    }

    @Test
    fun `replayOptionsFrom null map returns defaults`() {
        val adapter = newAdapter()
        val options = adapter.replayOptionsFrom(null)

        assertTrue(options.enabled)
        assertTrue(options.privacyProfile.maskTextInputs)
        assertFalse(options.privacyProfile.maskWebViews)
        assertFalse(options.privacyProfile.maskText)
        assertFalse(options.privacyProfile.maskImageViews)
    }

    @Test
    fun `replayOptionsFrom maps maskLabels key to maskText field`() {
        val adapter = newAdapter()
        val map = mockk<ReadableMap> {
            every { hasKey("maskLabels") } returns true
            every { getBoolean("maskLabels") } returns true
            every { hasKey("isEnabled") } returns false
            every { hasKey("maskTextInputs") } returns false
            every { hasKey("maskWebViews") } returns false
            every { hasKey("maskImages") } returns false
        }

        val options = adapter.replayOptionsFrom(map)

        assertTrue(options.privacyProfile.maskText)
    }

    @Test
    fun `ldContextFrom single-kind context`() {
        val adapter = newAdapter()
        val map = mockk<ReadableMap> {
            every { hasKey("kind") } returns true
            every { getString("kind") } returns "user"
            every { hasKey("key") } returns true
            every { getString("key") } returns "user-123"
        }

        val context = adapter.ldContextFrom(map)

        assertFalse(context.isMultiple)
        assertEquals(ContextKind.DEFAULT, context.kind)
        assertEquals("user-123", context.key)
    }

    @Test
    fun `ldContextFrom single-kind context with non-user kind`() {
        val adapter = newAdapter()
        val map = mockk<ReadableMap> {
            every { hasKey("kind") } returns true
            every { getString("kind") } returns "org"
            every { hasKey("key") } returns true
            every { getString("key") } returns "org-456"
        }

        val context = adapter.ldContextFrom(map)

        assertFalse(context.isMultiple)
        assertEquals(ContextKind.of("org"), context.kind)
        assertEquals("org-456", context.key)
    }

    @Test
    fun `ldContextFrom multi-kind context`() {
        val adapter = newAdapter()
        val map = mockk<ReadableMap> {
            every { hasKey("kind") } returns true
            every { getString("kind") } returns "multi"
            every { toHashMap() } returns hashMapOf(
                "kind" to "multi",
                "user" to hashMapOf("key" to "user-123"),
                "org" to hashMapOf("key" to "org-456"),
            )
        }

        val context = adapter.ldContextFrom(map)

        assertTrue(context.isMultiple)
        assertEquals(2, context.individualContextCount)
        val kinds = (0 until context.individualContextCount)
            .mapNotNull { context.getIndividualContext(it) }
            .associate { it.kind.toString() to it.key }
        assertEquals("user-123", kinds["user"])
        assertEquals("org-456", kinds["org"])
    }

    @Test
    fun `ldContextFrom legacy context without kind defaults to user`() {
        val adapter = newAdapter()
        val map = mockk<ReadableMap> {
            every { hasKey("kind") } returns false
            every { hasKey("key") } returns true
            every { getString("key") } returns "legacy-key"
        }

        val context = adapter.ldContextFrom(map)

        assertFalse(context.isMultiple)
        assertEquals(ContextKind.DEFAULT, context.kind)
        assertEquals("legacy-key", context.key)
    }

    @Test
    fun `start before setMobileKey calls completion with failure`() {
        val adapter = newAdapter()
        var success: Boolean? = null
        var errorMessage: String? = null

        adapter.start(mockk<Application>(relaxed = true)) { s, e ->
            success = s
            errorMessage = e
        }

        assertEquals(false, success)
        assertTrue(errorMessage!!.contains("mobile key"))
    }
}
