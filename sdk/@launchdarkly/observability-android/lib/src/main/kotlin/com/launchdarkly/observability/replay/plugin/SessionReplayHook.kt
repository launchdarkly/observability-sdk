package com.launchdarkly.observability.replay.plugin

import com.launchdarkly.sdk.android.integrations.Hook
import com.launchdarkly.sdk.android.integrations.IdentifySeriesContext
import com.launchdarkly.sdk.android.integrations.IdentifySeriesResult

/**
 * Hook protocol adapter for the native Android SDK.
 * Extracts data from SDK types and delegates to [SessionReplayHookExporter].
 */
class SessionReplayHook

internal constructor(
    private val exporter: SessionReplayHookExporter
) : Hook(HOOK_NAME) {

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
        val contextKeys = mutableMapOf<String, String>()
        val context = seriesContext.context
        if (context.isMultiple) {
            for (i in 0 until context.individualContextCount) {
                val individual = context.getIndividualContext(i)
                if (individual != null) {
                    contextKeys[individual.kind.toString()] = individual.key
                }
            }
        } else {
            contextKeys[context.kind.toString()] = context.key
        }

        exporter.afterIdentify(
            contextKeys = contextKeys,
            canonicalKey = context.fullyQualifiedKey,
            completed = result.status == IdentifySeriesResult.IdentifySeriesStatus.COMPLETED
        )
        return seriesData
    }

    companion object {
        const val HOOK_NAME: String = "Session Replay Hook"
    }
}
