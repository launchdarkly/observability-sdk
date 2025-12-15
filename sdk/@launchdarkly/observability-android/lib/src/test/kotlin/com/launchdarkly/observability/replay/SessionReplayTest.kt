package com.launchdarkly.observability.replay

import com.launchdarkly.logging.LDLogger
import com.launchdarkly.observability.api.ObservabilityOptions
import com.launchdarkly.observability.client.ObservabilityContext
import com.launchdarkly.observability.plugin.InstrumentationContributorManager
import com.launchdarkly.observability.sdk.LDObserve
import com.launchdarkly.sdk.android.LDClient
import io.mockk.mockk
import io.mockk.unmockkAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SessionReplayTest {

    private lateinit var client: LDClient

    @BeforeEach
    fun setUp() {
        InstrumentationContributorManager.reset()
        client = mockk(relaxed = true)
        LDObserve.context = null
    }

    @AfterEach
    fun tearDown() {
        InstrumentationContributorManager.reset()
        LDObserve.context = null
        unmockkAll()
    }

    @Test
    fun `register adds session replay when observability is initialized`() {
        LDObserve.context = ObservabilityContext(
            sdkKey = "test-sdk-key",
            options = ObservabilityOptions(),
            application = mockk(),
            logger = mockk<LDLogger>(relaxed = true),
        )
        val sessionReplay = SessionReplay()

        sessionReplay.register(client, null)

        val contributors = InstrumentationContributorManager.get(client)
        assertTrue(contributors.contains(sessionReplay))
        assertEquals(listOf(sessionReplay), contributors)
    }

    @Test
    fun `register doesn't add session replay when observability is not initialized`() {
        val sessionReplay = SessionReplay()
        sessionReplay.register(client, null)

        assertTrue(InstrumentationContributorManager.get(client).isEmpty())
    }

    @Test
    fun `provideInstrumentations returns replay instrumentation if observability is initialized`() {
        LDObserve.context = ObservabilityContext(
            sdkKey = "test-sdk-key",
            options = ObservabilityOptions(),
            application = mockk(),
            logger = mockk<LDLogger>(relaxed = true),
        )
        val sessionReplay = SessionReplay(ReplayOptions(debug = true))

        val instrumentations = sessionReplay.provideInstrumentations()
        assertEquals(1, instrumentations.size)
        assertTrue(instrumentations.first() is ReplayInstrumentation)
    }

    @Test
    fun `provideInstrumentations returns null if observability is not initialized`() {
        val sessionReplay = SessionReplay(ReplayOptions(debug = true))
        assertTrue(sessionReplay.provideInstrumentations().isEmpty())
    }

}
