package com.launchdarkly.observability.sdk

import android.app.Application
import com.launchdarkly.observability.api.ObservabilityOptions
import com.launchdarkly.observability.context.LDObserveContext
import com.launchdarkly.observability.plugin.Observability
import com.launchdarkly.observability.replay.ReplayOptions
import com.launchdarkly.observability.replay.plugin.SessionReplay
import com.launchdarkly.observability.testing.ObservabilityMainThreadTestHooks
import com.launchdarkly.sdk.android.LDClient
import com.launchdarkly.sdk.android.integrations.Plugin
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Covers the plugins [LDObserve.init] registers when handed an [LDClient].
 *
 * `registerPlugin` is stubbed rather than allowed to run the plugin lifecycle, so these tests are
 * about which plugins are registered and in what order — what the plugins then do on a live client
 * is covered by the e2e app.
 */
class LDObserveClientInitTest {

    private val registeredPlugins = mutableListOf<Plugin>()
    private lateinit var ldClient: LDClient

    @BeforeEach
    fun setUp() {
        // init registers on the main thread; run it on the calling thread instead.
        ObservabilityMainThreadTestHooks.overrideWithSynchronous()
        registeredPlugins.clear()
        ldClient = mockk(relaxed = true)
        every { ldClient.registerPlugin(capture(registeredPlugins)) } just Runs
    }

    @AfterEach
    fun tearDown() {
        ObservabilityMainThreadTestHooks.reset()
        LDObserve.context = null
    }

    @Test
    fun `registers observability before session replay`() {
        LDObserve.init(
            application = mockk<Application>(relaxed = true),
            ldClient = ldClient,
            ldContext = ldObserveContext(),
            observability = ObservabilityOptions(),
            replay = ReplayOptions(enabled = false),
        )

        // Session replay's registration reads the ObservabilityContext that observability's
        // registration publishes, so this order is load-bearing.
        assertEquals(2, registeredPlugins.size)
        assertInstanceOf(Observability::class.java, registeredPlugins[0])
        assertInstanceOf(SessionReplay::class.java, registeredPlugins[1])
    }

    @Test
    fun `registers only observability when replay options are absent`() {
        LDObserve.init(
            application = mockk<Application>(relaxed = true),
            ldClient = ldClient,
            ldContext = ldObserveContext(),
            observability = ObservabilityOptions(),
        )

        assertInstanceOf(Observability::class.java, registeredPlugins.single())
    }

    private fun ldObserveContext() =
        LDObserveContext.builder(LDObserveContext.DEFAULT_KIND, "example-user-key")
            .anonymous(true)
            .build()
}
