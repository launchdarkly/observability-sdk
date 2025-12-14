package com.launchdarkly.observability.replay

import com.launchdarkly.observability.BuildConfig
import com.launchdarkly.observability.interfaces.LDExtendedInstrumentation
import com.launchdarkly.observability.plugin.InstrumentationContributor
import com.launchdarkly.observability.plugin.InstrumentationContributorManager
import com.launchdarkly.observability.sdk.LDObserve
import com.launchdarkly.sdk.android.LDClient
import com.launchdarkly.sdk.android.integrations.EnvironmentMetadata
import com.launchdarkly.sdk.android.integrations.Plugin
import com.launchdarkly.sdk.android.integrations.PluginMetadata
import timber.log.Timber

/**
 * Session Replay plugin for the LaunchDarkly Android SDK.
 *
 * This plugin depends on the Observability plugin being present and initialized first.
 */
class SessionReplay(
    private val options: ReplayOptions = ReplayOptions(),
) : Plugin(), InstrumentationContributor {

    private val instrumentations: List<LDExtendedInstrumentation> by lazy {
        LDObserve.context?.let { context ->
            listOf(ReplayInstrumentation(options, context))
        }.orEmpty()
    }

    override fun getMetadata(): PluginMetadata {
        return object : PluginMetadata() {
            override fun getName(): String = PLUGIN_NAME
            override fun getVersion(): String = BuildConfig.OBSERVABILITY_SDK_VERSION
        }
    }

    override fun register(client: LDClient, metadata: EnvironmentMetadata?) {
        LDObserve.context?.let {
            InstrumentationContributorManager.add(client, this)
        } ?: run {
            Timber.tag(TAG).e("Observability plugin is not initialized")
        }
    }

    override fun provideInstrumentations(): List<LDExtendedInstrumentation> = instrumentations

    companion object {
        const val PLUGIN_NAME = "@launchdarkly/session-replay-android"
        private const val TAG = "SessionReplay"
    }
}
