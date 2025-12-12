package com.launchdarkly.observability.replay

import android.util.Log
import com.launchdarkly.observability.plugin.Observability
import com.launchdarkly.observability.plugin.PluginManager
import com.launchdarkly.sdk.android.LDClient
import com.launchdarkly.sdk.android.integrations.EnvironmentMetadata
import com.launchdarkly.sdk.android.integrations.Hook
import com.launchdarkly.sdk.android.integrations.Plugin
import com.launchdarkly.sdk.android.integrations.PluginMetadata
import com.launchdarkly.sdk.android.integrations.RegistrationCompleteResult
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SessionReplayTest {

    private lateinit var client: LDClient

    @BeforeEach
    fun setUp() {
        PluginManager.reset()
        client = mockk(relaxed = true)
    }

    @AfterEach
    fun tearDown() {
        PluginManager.reset()
        unmockkAll()
    }

    @Test
    fun `register adds session replay when observability is initialized`() {
        PluginManager.add(client, StubPlugin(Observability.PLUGIN_NAME))
        val sessionReplay = SessionReplay()

        sessionReplay.register(client, null)

        val plugins = PluginManager.get(client)
        assertTrue(plugins.contains(sessionReplay))
        assertEquals(listOf(sessionReplay), PluginManager.getInstrumentations(client))
    }

    @Test
    fun `register logs error when observability is not initialized`() {
        mockkStatic(Log::class)
        every { Log.e(any(), any()) } returns 0
        val sessionReplay = SessionReplay()

        sessionReplay.register(client, null)

        assertTrue(PluginManager.get(client).isEmpty())
        verify(exactly = 1) { Log.e("SessionReplay", "Observability plugin is not initialized") }
    }

    @Test
    fun `provideInstrumentations returns replay instrumentation with provided options`() {
        val options = ReplayOptions(serviceName = "service-x")
        val sessionReplay = SessionReplay(options)

        val instrumentations = sessionReplay.provideInstrumentations()

        assertEquals(1, instrumentations.size)
        val instrumentation = instrumentations.first()
        assertTrue(instrumentation is ReplayInstrumentation)
        val optionsField = ReplayInstrumentation::class.java.getDeclaredField("options")
        optionsField.isAccessible = true
        val capturedOptions = optionsField.get(instrumentation)
        assertSame(options, capturedOptions)
    }

    private open class StubPlugin(private val pluginName: String) : Plugin() {
        override fun getMetadata(): PluginMetadata {
            return object : PluginMetadata() {
                override fun getName(): String = pluginName
                override fun getVersion(): String = "test-version"
            }
        }

        override fun register(client: LDClient, metadata: EnvironmentMetadata?) = Unit
        override fun getHooks(metadata: EnvironmentMetadata?): MutableList<Hook> = mutableListOf()
        override fun onPluginsReady(result: RegistrationCompleteResult?, metadata: EnvironmentMetadata?) = Unit
    }
}
