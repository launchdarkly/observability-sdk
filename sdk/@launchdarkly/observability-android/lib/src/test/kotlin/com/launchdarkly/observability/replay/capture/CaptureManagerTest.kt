package com.launchdarkly.observability.replay.capture

import com.launchdarkly.observability.context.ObserveLogger
import com.launchdarkly.observability.replay.ReplayOptions
import io.mockk.mockk
import io.opentelemetry.android.session.SessionManager
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CaptureManagerTest {

    @Test
    fun `captureDelayMillis defaults to one second`() {
        val manager = captureManager()

        assertEquals(1000L, manager.captureDelayMillis)
    }

    @Test
    fun `captureDelayMillis is derived from frameRate`() {
        val manager = captureManager(ReplayOptions(frameRate = 2.0))

        assertEquals(500L, manager.captureDelayMillis)
    }

    private fun captureManager(options: ReplayOptions = ReplayOptions()) = CaptureManager(
        sessionManager = mockk<SessionManager>(relaxed = true),
        options = options,
        logger = mockk<ObserveLogger>(relaxed = true),
        imageCaptureService = mockk<ImageCaptureServicing>(relaxed = true),
    )
}
