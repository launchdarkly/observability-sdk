package com.launchdarkly.observability.replay.plugin

import com.launchdarkly.observability.coroutines.DispatcherProviderHolder
import com.launchdarkly.observability.replay.ReplayInstrumentation
import com.launchdarkly.sdk.android.integrations.Hook
import com.launchdarkly.sdk.android.integrations.IdentifySeriesContext
import com.launchdarkly.sdk.android.integrations.IdentifySeriesResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * This class is a hook implementation for recording flag evaluation and identify events
 * on spans.
 */
class SessionReplayHook

/**
 * Creates an [SessionReplayHook]
 *
 */
internal constructor(
    val plugin: SessionReplay
) : Hook(HOOK_NAME) {

    private val coroutineScope = CoroutineScope(DispatcherProviderHolder.current.io + SupervisorJob())

    override fun beforeIdentify(
        seriesContext: IdentifySeriesContext,
        seriesData: Map<String, Any>
    ): Map<String, Any> {
        return seriesData
    }

    override fun afterIdentify(
        seriesContext: IdentifySeriesContext,
        seriesData: Map<String, Any>,
        result: IdentifySeriesResult
    ): Map<String, Any> {
        if (result.status != IdentifySeriesResult.IdentifySeriesStatus.COMPLETED) {
            return seriesData
        }

        coroutineScope.launch {
            plugin.replayInstrumentation?.identifySession(seriesContext.context)
        }
        return seriesData
    }

    companion object {
        const val HOOK_NAME: String = "Session Replay Hook"
    }
}
