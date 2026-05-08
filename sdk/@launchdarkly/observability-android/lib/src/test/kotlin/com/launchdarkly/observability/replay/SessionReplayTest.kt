package com.launchdarkly.observability.replay

import com.launchdarkly.observability.context.ObserveLogger
import com.launchdarkly.observability.api.ObservabilityOptions
import com.launchdarkly.observability.client.ObservabilityContext
import com.launchdarkly.observability.replay.plugin.SessionReplayPluginImpl
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

    private fun newContext(): ObservabilityContext = ObservabilityContext(
        sdkKey = "test-sdk-key",
        options = ObservabilityOptions(),
        application = mockk(),
        logger = mockk<ObserveLogger>(relaxed = true),
    )

    @BeforeEach
    fun setUp() {
        // LDReplay is the global entry point this class wires up; reset it between tests.
        LDReplay.resetForTest()
    }

    @AfterEach
    fun tearDown() {
        LDReplay.resetForTest()
        unmockkAll()
    }

    @Test
    fun `register creates service but defers wiring LDReplay`() {
        val sessionReplay = SessionReplayPluginImpl()

        sessionReplay.register(newContext())

        assertNotNull(sessionReplay.sessionReplayService)
        assertNull(LDReplay.liveReplayService)
    }

    @Test
    fun `initialize wires up LDReplay after service creation`() {
        val sessionReplay = SessionReplayPluginImpl()

        sessionReplay.register(newContext())
        sessionReplay.initialize()

        assertNotNull(LDReplay.liveReplayService)
        assertTrue(LDReplay.liveReplayService is SessionReplayService)
    }

    @Test
    fun `register no-ops when LDReplay already has a client`() {
        // Use the real wiring API to install a client; tests no longer poke fields directly.
        LDReplay.init(mockk<SessionReplayService>(relaxed = true))
        val sessionReplay = SessionReplayPluginImpl()

        sessionReplay.register(newContext())

        assertNull(sessionReplay.sessionReplayService)
    }
}
