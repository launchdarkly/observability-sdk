package com.launchdarkly.observability.replay.plugin

import com.launchdarkly.observability.BuildConfig
import com.launchdarkly.observability.interfaces.LDExtendedInstrumentation
import com.launchdarkly.observability.plugin.InstrumentationContributor
import com.launchdarkly.observability.plugin.InstrumentationContributorManager
import com.launchdarkly.observability.replay.ReplayInstrumentation
import com.launchdarkly.observability.replay.ReplayOptions
import com.launchdarkly.observability.sdk.LDObserve
import com.launchdarkly.observability.sdk.LDReplay
import com.launchdarkly.sdk.android.LDClient
import com.launchdarkly.sdk.android.integrations.EnvironmentMetadata
import com.launchdarkly.sdk.android.integrations.Hook
import com.launchdarkly.sdk.android.integrations.Plugin
import com.launchdarkly.sdk.android.integrations.PluginMetadata
import timber.log.Timber
import java.util.Collections

/**
 * Session Replay plugin for the LaunchDarkly Android SDK.
 *
 * This plugin depends on the Observability plugin being present and initialized first.
 */
class SessionReplay(
    private val options: ReplayOptions = ReplayOptions(),
) : Plugin(), InstrumentationContributor {

    private var cachedInstrumentations: List<LDExtendedInstrumentation>? = null

    @Volatile
    var replayInstrumentation: ReplayInstrumentation? = null

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

    override fun provideInstrumentations(): List<LDExtendedInstrumentation> = synchronized(this) {
        val instrumentations = cachedInstrumentations ?: LDObserve.context?.let { context ->
            val instrumentation = ReplayInstrumentation(options, context).also { replayInstrumentation = it }
            listOf(instrumentation).also { cachedInstrumentations = it }
        }.orEmpty()

        replayInstrumentation?.let(LDReplay::init)
        instrumentations
    }

    override fun getHooks(metadata: EnvironmentMetadata?): MutableList<Hook> {
        return Collections.singletonList(
            SessionReplayHook(this)
        )
    }

    companion object {
        const val PLUGIN_NAME = "@launchdarkly/session-replay-android"
        private const val TAG = "SessionReplay"
    }
}
