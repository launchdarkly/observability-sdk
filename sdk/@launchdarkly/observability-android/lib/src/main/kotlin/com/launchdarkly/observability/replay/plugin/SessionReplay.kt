package com.launchdarkly.observability.replay.plugin

import com.launchdarkly.observability.BuildConfig
import com.launchdarkly.observability.replay.ReplayOptions
import com.launchdarkly.sdk.android.LDClient
import com.launchdarkly.sdk.android.integrations.EnvironmentMetadata
import com.launchdarkly.sdk.android.integrations.Hook
import com.launchdarkly.sdk.android.integrations.Plugin
import com.launchdarkly.sdk.android.integrations.PluginMetadata
import com.launchdarkly.sdk.android.integrations.RegistrationCompleteResult
import java.util.Collections

/**
 * LDClient plugin adapter for Session Replay.
 *
 * Wraps [SessionReplayImpl] so it can be registered as a [Plugin] with the LaunchDarkly
 * Android Client SDK. Only loaded when using the LDClient integration path.
 */
class SessionReplay(
    options: ReplayOptions = ReplayOptions(),
) : Plugin() {

    private val impl = SessionReplayImpl(options)
    private val sessionReplayHook = SessionReplayHook()

    val sessionReplayService get() = impl.sessionReplayService

    override fun getMetadata(): PluginMetadata {
        return object : PluginMetadata() {
            override fun getName(): String = SessionReplayImpl.PLUGIN_NAME
            override fun getVersion(): String = BuildConfig.OBSERVABILITY_SDK_VERSION
        }
    }

    override fun register(client: LDClient, metadata: EnvironmentMetadata?) {
        impl.register()
        sessionReplayHook.delegate = impl.sessionReplayService
    }

    override fun getHooks(metadata: EnvironmentMetadata?): MutableList<Hook> {
        return Collections.singletonList(sessionReplayHook)
    }

    override fun onPluginsReady(result: RegistrationCompleteResult?, metadata: EnvironmentMetadata?) {
        impl.initialize()
    }
}
