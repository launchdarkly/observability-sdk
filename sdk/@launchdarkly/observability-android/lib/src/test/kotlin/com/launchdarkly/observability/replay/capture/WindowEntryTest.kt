package com.launchdarkly.observability.replay.capture

import android.os.Build
import android.view.View
import android.view.WindowManager
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import io.mockk.mockk

class WindowEntryTest {
    private fun createEntry(
        type: WindowType = WindowType.ACTIVITY,
        layoutParams: WindowManager.LayoutParams? = null,
        width: Int = 100,
        height: Int = 100,
        screenLeft: Int = 0,
        screenTop: Int = 0
    ): WindowEntry {
        val rootView: View = mockk(relaxed = true)
        return WindowEntry(
            rootView = rootView,
            type = type,
            layoutParams = layoutParams,
            width = width,
            height = height,
            screenLeft = screenLeft,
            screenTop = screenTop
        )
    }

    @Test
    fun `non-activity windows are not candidates on O+`() {
        val entry = createEntry(
            type = WindowType.DIALOG,
            layoutParams = WindowManager.LayoutParams()
        )
        assertFalse(entry.isPixelCopyCandidate())
    }

    @Test
    fun `starting window is not a candidate on O+`() {
        val lp = WindowManager.LayoutParams().apply {
            type = WindowManager.LayoutParams.TYPE_APPLICATION_STARTING
        }
        val entry = createEntry(
            type = WindowType.ACTIVITY,
            layoutParams = lp
        )
        assertFalse(entry.isPixelCopyCandidate())
    }

    @Test
    fun `secure window is not a candidate on O+`() {
        val lp = WindowManager.LayoutParams().apply {
            flags = flags or WindowManager.LayoutParams.FLAG_SECURE
        }
        val entry = createEntry(
            type = WindowType.ACTIVITY,
            layoutParams = lp
        )
        assertFalse(entry.isPixelCopyCandidate())
    }

    @Test
    fun `activity non-secure non-starting window is a candidate on O+`() {
        val lp = WindowManager.LayoutParams() // default: not TYPE_APPLICATION_STARTING, no FLAG_SECURE
        val entry = createEntry(
            type = WindowType.ACTIVITY,
            layoutParams = lp
        )
        assertTrue(entry.isPixelCopyCandidate())
    }
}


