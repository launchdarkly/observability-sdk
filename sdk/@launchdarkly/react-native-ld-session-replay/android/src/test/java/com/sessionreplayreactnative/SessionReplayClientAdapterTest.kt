package com.sessionreplayreactnative

import android.app.Application
import com.facebook.react.bridge.ReadableMap
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
