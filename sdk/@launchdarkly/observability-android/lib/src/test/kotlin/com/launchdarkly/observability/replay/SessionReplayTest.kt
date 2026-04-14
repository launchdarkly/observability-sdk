package com.launchdarkly.observability.replay

import com.launchdarkly.logging.LDLogger
import com.launchdarkly.observability.api.ObservabilityOptions
import com.launchdarkly.observability.client.ObservabilityContext
import com.launchdarkly.observability.replay.plugin.SessionReplay
import com.launchdarkly.observability.sdk.LDObserve
import com.launchdarkly.observability.sdk.LDReplay
import io.mockk.mockk
import io.mockk.unmockkAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SessionReplayTest {

    @BeforeEach
    fun setUp() {
        LDObserve.context = null
        LDReplay.client = null
    }

    @AfterEach
    fun tearDown() {
        LDObserve.context = null
        LDReplay.client = null
        unmockkAll()
    }

    @Test
    fun `register creates service and wires up when observability is initialized`() {
        LDObserve.context = ObservabilityContext(
            sdkKey = "test-sdk-key",
            options = ObservabilityOptions(),
            application = mockk(),
            logger = mockk<LDLogger>(relaxed = true),
        )
        val sessionReplay = SessionReplay()

        sessionReplay.register()

        assertNotNull(sessionReplay.sessionReplayService)
        assertNotNull(LDReplay.client)
        assertTrue(LDReplay.client is SessionReplayService)
    }

    @Test
    fun `register does nothing when observability is not initialized`() {
        val sessionReplay = SessionReplay()
        sessionReplay.register()

        assertNull(sessionReplay.sessionReplayService)
        assertNull(LDReplay.client)
    }

}
