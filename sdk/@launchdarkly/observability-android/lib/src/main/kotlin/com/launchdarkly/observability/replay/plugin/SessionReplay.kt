package com.launchdarkly.observability.replay.plugin

import com.launchdarkly.observability.BuildConfig
import com.launchdarkly.observability.replay.ReplayOptions
import com.launchdarkly.observability.replay.capture.ImageCaptureServicing
import com.launchdarkly.observability.sdk.LDObserve
import com.launchdarkly.sdk.android.LDClient
import com.launchdarkly.sdk.android.integrations.EnvironmentMetadata
import com.launchdarkly.sdk.android.integrations.Plugin
import com.launchdarkly.sdk.android.integrations.PluginMetadata
import com.launchdarkly.sdk.android.integrations.RegistrationCompleteResult
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
class SessionReplay internal constructor(
    options: ReplayOptions,
    imageCaptureService: ImageCaptureServicing?,
) : Plugin() {

    private val impl = SessionReplayPluginImpl(options, imageCaptureService)

    @Volatile
    private var installed = false

    val sessionReplayService get() = impl.sessionReplayService

    /**
     * The replay service once it is recording, or `null` while it is absent or was never installed.
     *
     * Distinct from [sessionReplayService], which is non-null as soon as the service is constructed:
     * callers that drive the service themselves (notably the initial `identifySession`) must wait
     * for it to be published to [com.launchdarkly.observability.sdk.LDReplay].
     */
    internal val liveSessionReplayService get() = if (installed) impl.sessionReplayService else null

    /**
     * Creates a plugin to pass to [com.launchdarkly.sdk.android.LDConfig.Builder.plugins].
     */
    @Deprecated(
        "Pass replay options to LDObserve.init instead of adding this plugin to LDConfig."
    )
    constructor(
        options: ReplayOptions = ReplayOptions(),
    ) : this(options, imageCaptureService = null)

    override fun getMetadata(): PluginMetadata {
        return object : PluginMetadata() {
            override fun getName(): String = SessionReplayPluginImpl.PLUGIN_NAME
            override fun getVersion(): String = BuildConfig.OBSERVABILITY_SDK_VERSION
        }
    }

    override fun register(client: LDClient, metadata: EnvironmentMetadata?) {
        val obsContext = LDObserve.context ?: run {
            logger.warning(
                "Observability is not initialized; skipping SessionReplay registration. " +
                    "Ensure the Observability plugin is registered before SessionReplay."
            )
            return
        }
        impl.register(obsContext)
    }

    // Note: this plugin intentionally contributes no hooks. `Identify` and `Track` replay events are
    // recorded from Observability's single emitters via ObservabilityContext.identifyFlow and
    // trackFlow, so they cover both the LDClient calls and the manual LDObserve APIs without
    // double-recording. The native LDClient paths reach those emitters through ObservabilityHook.

    override fun onPluginsReady(result: RegistrationCompleteResult?, metadata: EnvironmentMetadata?) {
        installed = impl.initialize()
    }

    private companion object {
        private val logger = Logger.getLogger("SessionReplay")
    }
}
