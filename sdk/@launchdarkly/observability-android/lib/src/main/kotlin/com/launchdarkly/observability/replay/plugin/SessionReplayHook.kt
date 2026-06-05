package com.launchdarkly.observability.replay.plugin

import com.launchdarkly.observability.sdk.SessionReplayServicing
import com.launchdarkly.sdk.LDValueType
import com.launchdarkly.sdk.android.integrations.Hook
import com.launchdarkly.sdk.android.integrations.IdentifySeriesContext
import com.launchdarkly.sdk.android.integrations.IdentifySeriesResult
import com.launchdarkly.sdk.android.integrations.TrackSeriesContext
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes

/**
 * Hook protocol adapter for the native Android SDK.
 * Extracts data from SDK types and delegates to [SessionReplayServicing].
 */
class SessionReplayHook internal constructor() : Hook(HOOK_NAME) {

    @Volatile
    internal var delegate: SessionReplayServicing? = null

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
        val delegate = delegate ?: return seriesData

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

        delegate.afterIdentify(
            contextKeys = contextKeys,
            canonicalKey = context.fullyQualifiedKey,
            completed = result.status == IdentifySeriesResult.IdentifySeriesStatus.COMPLETED
        )
        return seriesData
    }

    override fun afterTrack(seriesContext: TrackSeriesContext) {
        val delegate = delegate ?: return

        val attrBuilder = Attributes.builder()
        val data = seriesContext.data
        if (data != null && data.type == LDValueType.OBJECT) {
            for (key in data.keys()) {
                val value = data.get(key)
                when (value.type) {
                    LDValueType.BOOLEAN -> attrBuilder.put(AttributeKey.booleanKey(key), value.booleanValue())
                    LDValueType.NUMBER -> attrBuilder.put(AttributeKey.doubleKey(key), value.doubleValue())
                    LDValueType.STRING -> attrBuilder.put(AttributeKey.stringKey(key), value.stringValue())
                    else -> {} // skip null/array/object
                }
            }
        }

        delegate.afterTrack(
            name = seriesContext.key,
            value = seriesContext.metricValue,
            attributes = attrBuilder.build()
        )
    }

    companion object {
        const val HOOK_NAME: String = "Session Replay Hook"
    }
}
