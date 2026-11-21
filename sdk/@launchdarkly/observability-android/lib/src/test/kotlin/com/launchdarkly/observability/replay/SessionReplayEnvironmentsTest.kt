package com.launchdarkly.observability.replay

import com.launchdarkly.observability.api.ObservabilityOptions
import com.launchdarkly.observability.client.ObservabilityContext
import com.launchdarkly.observability.context.ObserveLogger
import com.launchdarkly.observability.replay.plugin.SessionReplay
import com.launchdarkly.observability.replay.plugin.SessionReplayHook
import com.launchdarkly.observability.sdk.LDObserve
import com.launchdarkly.observability.sdk.LDReplay
import com.launchdarkly.observability.testing.ObservabilityMainThreadTestHooks
import com.launchdarkly.sdk.android.LDClient
import com.launchdarkly.sdk.android.integrations.EnvironmentMetadata
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * The SDK registers plugins with the client of every environment configured with
 * `secondaryMobileKeys`, in no particular order relative to the primary environment, after
 * publishing all of them, so `LDClient.get()` returns the primary client throughout.
 */
class SessionReplayEnvironmentsTest {

    private val primaryClient = mockk<LDClient>()
    private val secondaryClient = mockk<LDClient>()

    private fun metadata(credential: String): EnvironmentMetadata = mockk {
        every { this@mockk.credential } returns credential
    }

    @BeforeEach
    fun setUp() {
        LDReplay.resetForTest()
        ObservabilityMainThreadTestHooks.overrideWithSynchronous()
        mockkStatic(LDClient::class)
        every { LDClient.get() } returns primaryClient
        // Set by the Observability plugin when it registers with the primary environment.
        LDObserve.context = ObservabilityContext(
            sdkKey = PRIMARY_KEY,
            options = ObservabilityOptions(),
            application = mockk(),
            logger = mockk<ObserveLogger>(relaxed = true),
        )
    }

    @AfterEach
    fun tearDown() {
        LDObserve.context = null
        LDReplay.resetForTest()
        ObservabilityMainThreadTestHooks.reset()
        unmockkAll()
    }

    @Test
    fun `only the primary environment's hook is given the replay service when a secondary comes first`() {
        val plugin = SessionReplay()

        val secondaryHook = plugin.getHooks(metadata(SECONDARY_KEY)).single() as SessionReplayHook
        plugin.register(secondaryClient, metadata(SECONDARY_KEY))
        assertNull(plugin.sessionReplayService)

        val primaryHook = plugin.getHooks(metadata(PRIMARY_KEY)).single() as SessionReplayHook
        plugin.register(primaryClient, metadata(PRIMARY_KEY))

        assertNotNull(plugin.sessionReplayService)
        assertSame(plugin.sessionReplayService, primaryHook.delegate)
        assertNull(secondaryHook.delegate)
    }

    @Test
    fun `only the primary environment's hook is given the replay service when the primary comes first`() {
        val plugin = SessionReplay()

        val primaryHook = plugin.getHooks(metadata(PRIMARY_KEY)).single() as SessionReplayHook
        plugin.register(primaryClient, metadata(PRIMARY_KEY))
        val secondaryHook = plugin.getHooks(metadata(SECONDARY_KEY)).single() as SessionReplayHook
        plugin.register(secondaryClient, metadata(SECONDARY_KEY))

        assertNotNull(plugin.sessionReplayService)
        assertSame(plugin.sessionReplayService, primaryHook.delegate)
        assertNull(secondaryHook.delegate)
    }

    private companion object {
        const val PRIMARY_KEY = "mob-primary"
        const val SECONDARY_KEY = "mob-secondary"
    }
}
