package com.launchdarkly.observability.replay.plugin

import com.launchdarkly.observability.replay.ReplayOptions
import com.launchdarkly.observability.replay.SessionReplayService
import com.launchdarkly.observability.sdk.LDObserve
import com.launchdarkly.observability.sdk.LDObserveInternal
import com.launchdarkly.observability.sdk.LDReplayInternal
import java.util.logging.Logger

/**
 * Standalone Session Replay entry point.
 *
 * Use this directly via [LDObserve.init] for the standalone path (no LDClient).
 * For the LDClient plugin path, use [SessionReplay] instead.
 */
class SessionReplayImpl(
    private val options: ReplayOptions = ReplayOptions(),
) {
    @Volatile
    var sessionReplayService: SessionReplayService? = null

    fun register() {
        val context = LDObserveInternal.context ?: run {
            logger.warning("Observability is not initialized; skipping SessionReplay registration.")
            return
        }

        if (LDReplayInternal.client != null) {
            logger.warning("Session Replay instance already exists; skipping.")
            return
        }

        val service = SessionReplayService(options, context)
        LDReplayInternal.init(service)
        sessionReplayService = service
    }

    fun initialize() {
        val service = checkNotNull(sessionReplayService) {
            "SessionReplayService is not registered; call register() before initialize()."
        }
        service.initialize()
    }

    companion object {
        const val PLUGIN_NAME = "@launchdarkly/session-replay-android"
        private val logger = Logger.getLogger("SessionReplay")
    }
}
