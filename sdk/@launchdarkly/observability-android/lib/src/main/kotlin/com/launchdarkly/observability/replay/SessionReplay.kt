package com.launchdarkly.observability.replay

import android.util.Log
import com.launchdarkly.observability.BuildConfig
import com.launchdarkly.observability.client.PluginManager
import com.launchdarkly.observability.interfaces.LDExtendedInstrumentation
import com.launchdarkly.observability.plugin.InstrumentationContributor
import com.launchdarkly.sdk.android.LDClient
import com.launchdarkly.sdk.android.integrations.EnvironmentMetadata
import com.launchdarkly.sdk.android.integrations.Plugin
import com.launchdarkly.sdk.android.integrations.PluginMetadata

/**
 * Session Replay plugin for the LaunchDarkly Android SDK.
 *
 * This plugin depends on the Observability plugin being present and initialized first.
 */
class SessionReplay(
    private val options: ReplayOptions = ReplayOptions(),
) : Plugin(), InstrumentationContributor {

    override fun getMetadata(): PluginMetadata {
        return object : PluginMetadata() {
            override fun getName(): String = PLUGIN_NAME
            override fun getVersion(): String = BuildConfig.OBSERVABILITY_SDK_VERSION
        }
    }

    override fun register(client: LDClient, metadata: EnvironmentMetadata?) {
        if (PluginManager.isObservabilityInitialized(client)) {
            PluginManager.add(client, this)
        } else {
            Log.e("SessionReplay", "Observability plugin not initialized")
        }
    }

    override fun provideInstrumentations(): List<LDExtendedInstrumentation> {
        return listOf(ReplayInstrumentation(options))
    }

    companion object {
        const val PLUGIN_NAME = "@launchdarkly/session-replay-android"
    }
}
