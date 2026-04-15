package com.launchdarkly.observability.replay.plugin

import android.util.Log
import com.launchdarkly.observability.replay.ReplayOptions
import com.launchdarkly.observability.replay.SessionReplayService
import com.launchdarkly.observability.sdk.LDObserve
import com.launchdarkly.observability.sdk.LDReplay

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
        val context = LDObserve.context ?: run {
            Log.e(TAG, "Observability is not initialized; skipping SessionReplay registration.")
            return
        }

        if (LDReplay.client != null) {
            Log.e(TAG, "Session Replay instance already exists; skipping.")
            return
        }

        val service = SessionReplayService(options, context)
        LDReplay.init(service)
        sessionReplayService = service
    }

    fun initialize() {
        sessionReplayService?.initialize()
    }

    companion object {
        const val PLUGIN_NAME = "@launchdarkly/session-replay-android"
        private const val TAG = "SessionReplay"
    }
}
