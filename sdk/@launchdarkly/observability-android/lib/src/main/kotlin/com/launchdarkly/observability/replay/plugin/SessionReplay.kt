package com.launchdarkly.observability.replay.plugin

import com.launchdarkly.observability.BuildConfig
import com.launchdarkly.observability.replay.ReplayOptions
import com.launchdarkly.observability.sdk.LDObserve
import com.launchdarkly.sdk.android.LDClient
import com.launchdarkly.sdk.android.integrations.EnvironmentMetadata
import com.launchdarkly.sdk.android.integrations.Hook
import com.launchdarkly.sdk.android.integrations.Plugin
import com.launchdarkly.sdk.android.integrations.PluginMetadata
import com.launchdarkly.sdk.android.integrations.RegistrationCompleteResult
import java.util.Collections
import java.util.logging.Logger

/**
 * LDClient plugin adapter for Session Replay.
 *
 * Wraps [SessionReplayPluginImpl] so it can be registered as a [Plugin] with the LaunchDarkly
 * Android Client SDK. Only loaded when using the LDClient integration path.
 *
 * This adapter is the only place that resolves the [com.launchdarkly.observability.client.ObservabilityContext]
 * from the global [LDObserve.context]. The LDClient plugin lifecycle constructs plugins eagerly
 * and only hands them dependencies at [register], so we can't constructor-inject the context here.
 * Once we have it, we forward it to [SessionReplayPluginImpl] explicitly — keeping the global lookup
 * confined to this boundary.
 */
class SessionReplay(
    options: ReplayOptions = ReplayOptions(),
) : Plugin() {

    private val impl = SessionReplayPluginImpl(options)
    private val sessionReplayHook = SessionReplayHook()

    val sessionReplayService get() = impl.sessionReplayService

    override fun getMetadata(): PluginMetadata {
        return object : PluginMetadata() {
            override fun getName(): String = SessionReplayPluginImpl.PLUGIN_NAME
            override fun getVersion(): String = BuildConfig.OBSERVABILITY_SDK_VERSION
        }
    }

    override fun register(client: LDClient, metadata: EnvironmentMetadata?) {
        val context = LDObserve.context ?: run {
            logger.warning(
                "Observability is not initialized; skipping SessionReplay registration. " +
                    "Ensure the Observability plugin is registered before SessionReplay."
            )
            return
        }
        impl.register(context)
        sessionReplayHook.delegate = impl.sessionReplayService
    }

    override fun getHooks(metadata: EnvironmentMetadata?): MutableList<Hook> {
        return Collections.singletonList(sessionReplayHook)
    }

    override fun onPluginsReady(result: RegistrationCompleteResult?, metadata: EnvironmentMetadata?) {
        impl.initialize()
    }

    private companion object {
        private val logger = Logger.getLogger("SessionReplay")
    }
}
