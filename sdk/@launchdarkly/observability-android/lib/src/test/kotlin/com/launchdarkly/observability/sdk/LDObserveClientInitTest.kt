package com.launchdarkly.observability.sdk

import android.app.Application
import com.launchdarkly.observability.api.ObservabilityOptions
import com.launchdarkly.observability.context.LDObserveContext
import com.launchdarkly.observability.context.ObserveLogAdapter
import com.launchdarkly.observability.context.ObserveLogLevel
import com.launchdarkly.observability.plugin.Observability
import com.launchdarkly.observability.replay.ReplayOptions
import com.launchdarkly.observability.testing.ObservabilityMainThreadTestHooks
import com.launchdarkly.sdk.android.LDClient
import com.launchdarkly.sdk.android.integrations.Plugin
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Covers the plugins [LDObserve.init] registers when handed an [LDClient].
 *
 * `registerPlugin` is stubbed rather than allowed to run the plugin lifecycle, so these tests are
 * about which plugins reach the client — what they then do on a live client is covered by the e2e
 * app.
 */
class LDObserveClientInitTest {

    private val registeredPlugins = mutableListOf<Plugin>()
    private val logMessages = mutableListOf<String>()
    private lateinit var ldClient: LDClient

    @BeforeEach
    fun setUp() {
        // init registers on the main thread; run it on the calling thread instead.
        ObservabilityMainThreadTestHooks.overrideWithSynchronous()
        registeredPlugins.clear()
        logMessages.clear()
        ldClient = mockk(relaxed = true)
        every { ldClient.registerPlugin(capture(registeredPlugins)) } just Runs
    }

    @AfterEach
    fun tearDown() {
        ObservabilityMainThreadTestHooks.reset()
        LDObserve.context = null
    }

    @Test
    fun `registers observability only, installing session replay off the client`() {
        LDObserve.init(
            application = mockk<Application>(relaxed = true),
            ldClient = ldClient,
            ldContext = ldObserveContext(),
            observability = observabilityOptions(),
            replay = ReplayOptions(enabled = false),
        )

        // Session replay contributes no hooks, so it is installed onto the ObservabilityContext
        // instead of being registered as a plugin.
        assertInstanceOf(Observability::class.java, registeredPlugins.single())
        // Stubbing registerPlugin leaves that context unpublished, which the install reports rather
        // than throwing on.
        assertTrue(logMessages.any { it.contains("skipping session replay") }, "logged: $logMessages")
    }

    @Test
    fun `registers observability when replay options are absent`() {
        LDObserve.init(
            application = mockk<Application>(relaxed = true),
            ldClient = ldClient,
            ldContext = ldObserveContext(),
            observability = observabilityOptions(),
        )

        assertInstanceOf(Observability::class.java, registeredPlugins.single())
    }

    private fun ldObserveContext() =
        LDObserveContext.builder(LDObserveContext.DEFAULT_KIND, "example-user-key")
            .anonymous(true)
            .build()

    /** Options that log into [logMessages], since android.util.Log is not available here. */
    private fun observabilityOptions() = ObservabilityOptions(
        logAdapter = ObserveLogAdapter {
            object : ObserveLogAdapter.Channel {
                override fun log(level: ObserveLogLevel, message: String) {
                    logMessages += message
                }
            }
        },
    )
}
