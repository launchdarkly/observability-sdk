package com.launchdarkly.observability.replay.plugin

import com.launchdarkly.observability.BuildConfig
import com.launchdarkly.observability.replay.ReplayOptions
import com.launchdarkly.observability.replay.SessionReplayService
import com.launchdarkly.observability.sdk.LDObserve
import com.launchdarkly.observability.sdk.LDReplay
import com.launchdarkly.sdk.android.LDClient
import com.launchdarkly.sdk.android.integrations.EnvironmentMetadata
import com.launchdarkly.sdk.android.integrations.Hook
import com.launchdarkly.sdk.android.integrations.Plugin
import com.launchdarkly.sdk.android.integrations.PluginMetadata
import com.launchdarkly.sdk.android.integrations.RegistrationCompleteResult
import timber.log.Timber
import java.util.Collections

/**
 * Session Replay plugin for the LaunchDarkly Android SDK.
 *
 * This plugin depends on the Observability plugin being present and initialized first.
 */
class SessionReplay(
    private val options: ReplayOptions = ReplayOptions(),
) : Plugin() {

    private val sessionReplayHook = SessionReplayHook()

    @Volatile
    var sessionReplayService: SessionReplayService? = null

    override fun getMetadata(): PluginMetadata {
        return object : PluginMetadata() {
            override fun getName(): String = PLUGIN_NAME
            override fun getVersion(): String = BuildConfig.OBSERVABILITY_SDK_VERSION
        }
    }

    override fun register(client: LDClient, metadata: EnvironmentMetadata?) {
        register()
    }

    fun register() {
        val context = LDObserve.context ?: run {
            Timber.tag(TAG).e("Observability plugin is not initialized")
            return
        }

        if (LDReplay.client != null) {
            Timber.tag(TAG).e("Session Replay instance already exists")
            return
        }

        val service = SessionReplayService(options, context)
        LDReplay.init(service)
        sessionReplayService = service
        sessionReplayHook.delegate = service
    }

    override fun getHooks(metadata: EnvironmentMetadata?): MutableList<Hook> {
        return Collections.singletonList(sessionReplayHook)
    }

    override fun onPluginsReady(result: RegistrationCompleteResult?, metadata: EnvironmentMetadata?) {
        sessionReplayService?.initialize()
    }

    companion object {
        const val PLUGIN_NAME = "@launchdarkly/session-replay-android"
        private const val TAG = "SessionReplay"
    }
}
