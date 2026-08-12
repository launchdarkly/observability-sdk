package com.launchdarkly.observability.sdk

import com.launchdarkly.observability.api.ObservabilityOptions
import com.launchdarkly.observability.client.DefaultInstrumentation
import com.launchdarkly.observability.client.ObservabilityContext
import com.launchdarkly.observability.client.ObservabilityInstrumenting
import com.launchdarkly.observability.context.LDObserveContext
import com.launchdarkly.observability.context.ObserveLogger
import com.launchdarkly.observability.coroutines.DispatcherProviderHolder
import com.launchdarkly.observability.replay.ReplayOptions
import com.launchdarkly.observability.replay.capture.ImageCaptureServicing
import com.launchdarkly.observability.replay.plugin.SessionReplayPluginImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Makes the full product's instrumentation and Session Replay reachable from the standalone
 * [LDObserve.init] path.
 *
 * Instantiated reflectively by [ObservabilityExtensionsLoader] purely on the basis of this artifact
 * being on the classpath, so nothing here is referenced statically. That is deliberate: the
 * OTel-only core must not name any of the types below.
 */
internal class FullObservabilityExtensions : ObservabilityExtensions {

    override fun createInstrumentation(
        sdkKey: String,
        options: ObservabilityOptions,
        logger: ObserveLogger,
    ): ObservabilityInstrumenting = DefaultInstrumentation(sdkKey, options, logger)

    override val sessionReplayInstaller: SessionReplayInstalling = Installer

    private object Installer : SessionReplayInstalling {
        override fun install(
            replay: ReplayConfiguration,
            obsContext: ObservabilityContext,
            ldContext: LDObserveContext,
            imageCapture: ImageCapturing?,
        ) {
            // The markers exist so the core can carry these across without naming them. A value of
            // another type cannot reach here: `ReplayOptions` and `ImageCaptureServicing` are their
            // only implementations.
            val options = replay as? ReplayOptions ?: return
            val plugin = SessionReplayPluginImpl(options, imageCapture as? ImageCaptureServicing)
            plugin.register(obsContext)
            if (!plugin.initialize()) return
            val replayService = plugin.sessionReplayService ?: return
            CoroutineScope(DispatcherProviderHolder.current.default + SupervisorJob()).launch {
                replayService.identifySession(ldContext)
            }
        }
    }
}
