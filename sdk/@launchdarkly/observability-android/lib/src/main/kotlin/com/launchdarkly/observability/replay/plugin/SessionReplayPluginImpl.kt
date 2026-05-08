package com.launchdarkly.observability.replay.plugin

import com.launchdarkly.observability.client.ObservabilityContext
import com.launchdarkly.observability.replay.ReplayOptions
import com.launchdarkly.observability.replay.SessionReplayService
import com.launchdarkly.observability.sdk.LDObserve
import com.launchdarkly.observability.sdk.LDReplay
import java.util.logging.Logger

/**
 * Shared Session Replay registration logic used by both initialization paths.
 *
 * Use this directly via [LDObserve.init] for the standalone path (no LDClient).
 * For the LDClient plugin path, use [SessionReplay] (which delegates to this class) instead.
 */
class SessionReplayPluginImpl(
    private val options: ReplayOptions = ReplayOptions(),
) {
    @Volatile
    var sessionReplayService: SessionReplayService? = null

    /**
     * Registers a [SessionReplayService] backed by [observabilityContext] and wires it into
     * [LDReplay] as the active replay service.
     *
     * Dependencies are passed in explicitly rather than resolved from `LDObserve.context` so
     * this class has no hidden coupling to global state — callers are responsible for ensuring
     * observability has been initialized and providing its context.
     *
     * No-ops if Session Replay has already been registered globally on [LDReplay].
     */
    fun register(observabilityContext: ObservabilityContext) {
        if (sessionReplayService != null || LDReplay.client != null) {
            logger.warning("Session Replay instance already exists; skipping.")
            return
        }

        val service = SessionReplayService(options, observabilityContext)
        sessionReplayService = service
    }

    fun initialize() {
        val service = sessionReplayService ?: return
        service.initialize()
        LDReplay.init(service)
    }

    companion object {
        const val PLUGIN_NAME = "@launchdarkly/session-replay-android"
        private val logger = Logger.getLogger("SessionReplay")
    }
}
