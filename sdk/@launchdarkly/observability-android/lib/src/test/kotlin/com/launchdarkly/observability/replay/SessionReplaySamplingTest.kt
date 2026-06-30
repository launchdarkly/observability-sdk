package com.launchdarkly.observability.replay

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SessionReplaySamplingTest {

    @Test
    fun `sampleRate defaults to always sample`() {
        assertEquals(1.0, ReplayOptions().sampleRate)
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
    fun `sampling decision is not re-evaluated after sampled out`() {
        val session = SessionReplaySamplingSession()
        assertFalse(
            session.shouldStartCapture(
                ignoreSampling = false,
                sampleRate = 0.25,
                randomValue = { 0.99 },
            )
        )
        assertFalse(
            session.shouldStartCapture(
                ignoreSampling = false,
                sampleRate = 0.25,
                randomValue = { 0.0 },
            )
        )
        session.reset()
        assertTrue(
            session.shouldStartCapture(
                ignoreSampling = false,
                sampleRate = 0.25,
                randomValue = { 0.0 },
            )
        )
    }

    @Test
    fun `ignoreSampling bypasses persisted sampled-out decision`() {
        val session = SessionReplaySamplingSession()
        assertFalse(
            session.shouldStartCapture(
                ignoreSampling = false,
                sampleRate = 0.25,
                randomValue = { 0.99 },
            )
        )
        assertTrue(
            session.shouldStartCapture(
                ignoreSampling = true,
                sampleRate = 0.25,
                randomValue = { 0.99 },
            )
        )
    }
}
