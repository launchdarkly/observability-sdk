package com.launchdarkly.observability.replay

import com.launchdarkly.observability.sdk.SessionReplayStartResult
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SessionReplaySamplingTest {
    @Test
    fun `sampleRate defaults to always sample`() {
        assertTrue(ReplayOptions().sampleRate == 1.0)
        assertTrue(SessionReplaySampling.shouldSample(sampleRate = 1.0) { 0.99 })
    }

    @Test
    fun `sampleRate zero disables session replay`() {
        assertFalse(SessionReplaySampling.shouldSample(sampleRate = 0.0) { 0.0 })
    }

    @Test
    fun `sampleRate samples when random value is below rate`() {
        assertTrue(SessionReplaySampling.shouldSample(sampleRate = 0.5) { 0.49 })
        assertFalse(SessionReplaySampling.shouldSample(sampleRate = 0.5) { 0.5 })
    }

    @Test
    fun `start result indicates whether session replay is running`() {
        assertTrue(SessionReplayStartResult.STARTED.isRunning)
        assertTrue(SessionReplayStartResult.ALREADY_STARTED.isRunning)
        assertFalse(SessionReplayStartResult.SAMPLED_OUT.isRunning)
        assertFalse(SessionReplayStartResult.UNAVAILABLE.isRunning)
    }
}
