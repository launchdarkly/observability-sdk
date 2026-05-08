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
     * Creates a [SessionReplayService] backed by [observabilityContext]. Wiring into [LDReplay]
     * happens later in [initialize].
     *
     * Dependencies are passed in explicitly rather than resolved from `LDObserve.context` so
     * this class has no hidden coupling to global state — callers are responsible for ensuring
     * observability has been initialized and providing its context.
     *
     * No-ops (with a warning) if either:
     *  - this instance was already registered, or
     *  - some other [SessionReplayPluginImpl] instance already won the global registration race
     *    on [LDReplay] (e.g. both the standalone and LDClient plugin paths were used).
     */
    fun register(observabilityContext: ObservabilityContext) {
        if (sessionReplayService != null) {
            logger.warning("Session Replay already registered on this plugin instance; skipping.")
            return
        }
        if (LDReplay.liveReplayService != null) {
            logger.warning("Session Replay already registered globally on LDReplay; skipping.")
            return
        }

        sessionReplayService = SessionReplayService(options, observabilityContext)
    }

    /**
     * Initializes the service produced by [register] and publishes it to [LDReplay], draining
     * any pre-init buffer.
     *
     * Returns `true` when the live service was published to [LDReplay] — i.e. post-init steps
     * that depend on a fully installed service (most notably the initial `identifySession`
     * kick-off in [com.launchdarkly.observability.sdk.LDObserve.init]) are safe to run.
     * Callers without post-publish work can ignore the return value.
     */
    fun initialize(): Boolean {
        val service = sessionReplayService ?: run {
            logger.warning(
                "Session Replay initialize() called before register() produced a service; skipping."
            )
            return false
        }
        if (!service.initialize()) {
            logger.warning(
                "Session Replay service did not install (likely because Observability " +
                    "is not registered or its sessionManager is unavailable); skipping LDReplay wiring."
            )
            return false
        }
        LDReplay.init(service)
        return true
    }

    companion object {
        const val PLUGIN_NAME = "@launchdarkly/session-replay-android"
        private val logger = Logger.getLogger("SessionReplay")
    }
}
