package com.launchdarkly.observability.sdk

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PreInitReplayBufferTest {

    private class CountingReplayService(
        isEnabled: Boolean,
    ) : SessionReplayServicing {
        private var enabled = isEnabled

        override var isEnabled: Boolean
            get() = enabled
            set(value) {
                enabled = value
                if (value) {
                    start(ignoreSampling = false)
                } else {
                    isRunning = false
                }
            }

        override var isRunning: Boolean = false
        var startCalls = 0

        override fun start(ignoreSampling: Boolean): SessionReplayStartResult {
            startCalls++
            enabled = true
            isRunning = true
            return SessionReplayStartResult.STARTED
        }

        override fun flush() {}

        override fun afterIdentify(
            contextKeys: Map<String, String>,
            canonicalKey: String,
            completed: Boolean
        ) {
        }
    }

    @Test
    fun `bind does not re-enable when service already enabled`() {
        val buffer = PreInitReplayBuffer()
        buffer.setEnabled(true)

        val replayService = CountingReplayService(isEnabled = true)
        buffer.bind(replayService)

        assertEquals(0, replayService.startCalls)
        assertTrue(replayService.isEnabled)
    }

    @Test
    fun `bind applies buffered enable when service is still disabled`() {
        val buffer = PreInitReplayBuffer()
        buffer.setEnabled(true)

        val replayService = CountingReplayService(isEnabled = false)
        buffer.bind(replayService)

        assertEquals(1, replayService.startCalls)
        assertTrue(replayService.isEnabled)
    }

    /**
     * Models [SessionReplayService.initialize] with [ReplayOptions.enabled] after a sampled-out
     * [start]: enabled intent is set but capture is not running.
     */
    private class SampledOutReplayService : SessionReplayServicing {
        override var isEnabled: Boolean = true
        override var isRunning: Boolean = false
        var startCalls = 0

        override fun start(ignoreSampling: Boolean): SessionReplayStartResult {
            startCalls++
            isEnabled = true
            if (ignoreSampling) {
                isRunning = true
                return SessionReplayStartResult.STARTED
            }
            isRunning = false
            return SessionReplayStartResult.SAMPLED_OUT
        }

        override fun flush() {}

        override fun afterIdentify(
            contextKeys: Map<String, String>,
            canonicalKey: String,
            completed: Boolean
        ) {
        }
    }

    @Test
    fun `bind does not re-roll sampling after options-based sampled-out start`() {
        val buffer = PreInitReplayBuffer()
        buffer.start(ignoreSampling = false)

        val replayService = SampledOutReplayService()
        buffer.bind(replayService)

        assertEquals(0, replayService.startCalls)
        assertTrue(replayService.isEnabled)
    }
}
