package com.launchdarkly.observability.replay

import com.launchdarkly.observability.context.ObserveLogger
import com.launchdarkly.observability.api.ObservabilityOptions
import com.launchdarkly.observability.client.ObservabilityContext
import com.launchdarkly.observability.replay.plugin.SessionReplayImpl
import com.launchdarkly.observability.sdk.LDObserveInternal
import com.launchdarkly.observability.sdk.LDReplayInternal
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
        LDObserveInternal.context = null
        LDReplayInternal.client = null
    }

    @AfterEach
    fun tearDown() {
        LDObserveInternal.context = null
        LDReplayInternal.client = null
        unmockkAll()
    }

    @Test
    fun `register creates service and wires up when observability is initialized`() {
        LDObserveInternal.context = ObservabilityContext(
            sdkKey = "test-sdk-key",
            options = ObservabilityOptions(),
            application = mockk(),
            logger = mockk<ObserveLogger>(relaxed = true),
        )
        val sessionReplay = SessionReplayImpl()

        sessionReplay.register()

        assertNotNull(sessionReplay.sessionReplayService)
        assertNotNull(LDReplayInternal.client)
        assertTrue(LDReplayInternal.client is SessionReplayService)
    }

    @Test
    fun `register does nothing when observability is not initialized`() {
        val sessionReplay = SessionReplayImpl()
        sessionReplay.register()

        assertNull(sessionReplay.sessionReplayService)
        assertNull(LDReplayInternal.client)
    }

    @Test
    fun `register does nothing when session replay already exists`() {
        LDObserveInternal.context = ObservabilityContext(
            sdkKey = "test-sdk-key",
            options = ObservabilityOptions(),
            application = mockk(),
            logger = mockk<ObserveLogger>(relaxed = true),
        )
        LDReplayInternal.client = mockk<SessionReplayService>(relaxed = true)
        val sessionReplay = SessionReplayImpl()

        sessionReplay.register()

        assertNull(sessionReplay.sessionReplayService)
    }

}
