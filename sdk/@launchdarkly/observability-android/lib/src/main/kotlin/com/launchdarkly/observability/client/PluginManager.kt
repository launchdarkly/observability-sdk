package com.launchdarkly.observability.client

import com.launchdarkly.observability.plugin.InstrumentationContributor
import com.launchdarkly.observability.plugin.Observability
import com.launchdarkly.sdk.android.LDClient
import com.launchdarkly.sdk.android.integrations.Plugin
import java.util.WeakHashMap

/**
 * Manages a collection of plugins associated with an [LDClient] instance.
 *
 * This object provides a central place to register and retrieve plugins for a given LDClient.
 * It uses a [WeakHashMap] to store plugins, which allows the [LDClient] instances and
 * associated plugins to be garbage collected when they are no longer in use.
 */
internal object PluginManager {
    private val plugins = WeakHashMap<LDClient, MutableList<Plugin>>()

    /**
     * Adds a [Plugin] to the list of plugins associated with the given [LDClient].
     *
     * If no plugins have been added for the client before, a new list is created.
     *
     * @param client The [LDClient] to associate the plugin with.
     * @param plugin The [Plugin] to add.
     */
    fun add(client: LDClient, plugin: Plugin) {
        synchronized(plugins) {
            plugins.getOrPut(client) { mutableListOf() }.add(plugin)
        }
    }

    /**
     * Retrieves the list of [Plugin]s associated with the given [LDClient].
     *
     * The returned list is a snapshot and is safe to iterate over.
     *
     * @param client The [LDClient] to get the plugins for.
     * @return A list of [Plugin]s, or null if no plugins are associated with the client.
     */
    fun get(client: LDClient): List<Plugin>? = synchronized(plugins) {
        plugins[client]?.toList()
    }

    /**
     * Retrieves the list of [InstrumentationContributor] plugins associated with the given [LDClient].
     *
     * @param client The [LDClient] to get the instrumentations for.
     * @return A list of [InstrumentationContributor]s, or null if no plugins are associated with the client.
     */
    fun getInstrumentations(client: LDClient): List<InstrumentationContributor>? = synchronized(plugins) {
        plugins[client]?.filterIsInstance<InstrumentationContributor>()
    }

    /**
     * Checks if the observability plugin has been initialized for the given [LDClient].
     *
     * @param client The [LDClient] to check.
     * @return True if the observability plugin is initialized, false otherwise.
     */
    fun isObservabilityInitialized(client: LDClient): Boolean {
        return get(client)?.any { it.metadata.name == Observability.PLUGIN_NAME } == true
    }
}
