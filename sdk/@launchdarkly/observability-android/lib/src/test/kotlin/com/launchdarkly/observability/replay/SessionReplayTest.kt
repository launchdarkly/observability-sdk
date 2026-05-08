package com.launchdarkly.observability.replay

import com.launchdarkly.observability.context.ObserveLogger
import com.launchdarkly.observability.api.ObservabilityOptions
import com.launchdarkly.observability.client.ObservabilityContext
import com.launchdarkly.observability.replay.plugin.SessionReplayPluginImpl
import com.launchdarkly.observability.sdk.LDReplay
import com.launchdarkly.observability.testing.ObservabilityMainThreadTestHooks
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
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
        // SessionReplayService.initialize() and PreInitReplayBuffer dispatches both go through
        // the main-thread executor, which would otherwise hit Android's main Looper.
        ObservabilityMainThreadTestHooks.overrideWithSynchronous()
    }

    @AfterEach
    fun tearDown() {
        LDReplay.resetForTest()
        ObservabilityMainThreadTestHooks.reset()
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
    fun `initialize wires up LDReplay when service install succeeds`() {
        // Substitute a stub service for the one register() created so we can decide the
        // SessionReplayService.initialize() outcome without standing up a real SessionManager,
        // ProcessLifecycleOwner, etc. — none of which are available in plain JVM tests.
        val service = mockk<SessionReplayService>(relaxed = true)
        every { service.initialize() } returns true
        val sessionReplay = SessionReplayPluginImpl().apply {
            register(newContext())
            sessionReplayService = service
        }

        sessionReplay.initialize()

        assertSame(service, LDReplay.liveReplayService)
    }

    @Test
    fun `initialize skips LDReplay wiring when service install fails`() {
        // Reproduces the bug where SessionReplayService.initialize() bails out (e.g. because
        // ObservabilityContext.sessionManager is still null on the LDClient plugin path):
        // the plugin must NOT publish a non-functional service to LDReplay, otherwise every
        // subsequent LDReplay call routes to a dead instance with no recovery path.
        val service = mockk<SessionReplayService>(relaxed = true)
        every { service.initialize() } returns false
        val sessionReplay = SessionReplayPluginImpl().apply {
            register(newContext())
            sessionReplayService = service
        }

        sessionReplay.initialize()

        assertNull(LDReplay.liveReplayService)
        verify(exactly = 1) { service.initialize() }
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
