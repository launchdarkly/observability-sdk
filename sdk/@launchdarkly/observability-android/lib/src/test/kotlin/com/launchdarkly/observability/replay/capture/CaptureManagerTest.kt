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

    @Test
    fun `effectiveCaptureDelayMillis defaults to base cadence`() {
        val manager = captureManager(ReplayOptions(frameRate = 2.0))

        assertEquals(500L, manager.effectiveCaptureDelayMillis())
    }

    @Test
    fun `effectiveCaptureDelayMillis pins to base cadence right after an interaction`() {
        val manager = captureManager(ReplayOptions(frameRate = 2.0))

        manager.noteInteraction()

        assertEquals(500L, manager.effectiveCaptureDelayMillis())
    }

    @Test
    fun `effectiveCaptureDelayMillis is MAX_VALUE when frameRate is zero`() {
        val manager = captureManager(ReplayOptions(frameRate = 0.0))

        assertEquals(Long.MAX_VALUE, manager.effectiveCaptureDelayMillis())
    }

    private fun captureManager(options: ReplayOptions = ReplayOptions()) = CaptureManager(
        sessionManager = mockk<SessionManager>(relaxed = true),
        options = options,
        logger = mockk<ObserveLogger>(relaxed = true),
        imageCaptureService = mockk<ImageCaptureServicing>(relaxed = true),
    )
}
