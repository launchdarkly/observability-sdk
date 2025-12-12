package com.launchdarkly.observability.plugin

import com.launchdarkly.observability.interfaces.LDExtendedInstrumentation
import com.launchdarkly.sdk.android.LDClient
import com.launchdarkly.sdk.android.integrations.EnvironmentMetadata
import com.launchdarkly.sdk.android.integrations.Hook
import com.launchdarkly.sdk.android.integrations.Plugin
import com.launchdarkly.sdk.android.integrations.PluginMetadata
import com.launchdarkly.sdk.android.integrations.RegistrationCompleteResult
import io.mockk.mockk
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class PluginManagerTest {

    private lateinit var client: LDClient

    @BeforeEach
    fun setUp() {
        client = mockk()
    }

    @AfterEach
    fun tearDown() {
        PluginManager.reset()
    }

    @Test
    fun `add() stores plugins associated to a client`() {
        val pluginOne = StubPlugin("plugin-one")
        val pluginTwo = StubPlugin("plugin-two")
        val pluginThree = StubPlugin("plugin-three")

        PluginManager.add(client, pluginOne)
        PluginManager.add(client, pluginTwo)

        val firstSnapshot = PluginManager.get(client)

        PluginManager.add(client, pluginThree)

        val secondSnapshot = PluginManager.get(client)

        assertEquals(listOf(pluginOne, pluginTwo), firstSnapshot)
        assertEquals(listOf(pluginOne, pluginTwo, pluginThree), secondSnapshot)
    }

    @Test
    fun `getInstrumentations returns only instrumentation contributors for client`() {
        val instrumentationPlugin = StubInstrumentationContributorPlugin()
        val regularPlugin = StubPlugin("regular-plugin")

        PluginManager.add(client, regularPlugin)
        PluginManager.add(client, instrumentationPlugin)

        val instrumentations = PluginManager.getInstrumentations(client)

        assertEquals(listOf(instrumentationPlugin), instrumentations)
    }

    @Test
    fun `isObservabilityInitialized reports presence of observability plugin`() {
        PluginManager.add(client, StubPlugin("other-plugin"))

        assertFalse(PluginManager.isObservabilityInitialized(client))

        PluginManager.add(client, StubPlugin(Observability.PLUGIN_NAME))

        assertTrue(PluginManager.isObservabilityInitialized(client))
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

    private class StubInstrumentationContributorPlugin : StubPlugin("instrumentation-contributor-plugin"), InstrumentationContributor {
        private val instrumentation: LDExtendedInstrumentation = mockk()

        override fun provideInstrumentations(): List<LDExtendedInstrumentation> = listOf(instrumentation)
    }
}
