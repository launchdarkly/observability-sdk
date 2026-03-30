package com.launchdarkly.observability.plugin

import com.launchdarkly.sdk.EvaluationDetail
import com.launchdarkly.sdk.LDValue
import com.launchdarkly.sdk.android.integrations.EvaluationSeriesContext
import com.launchdarkly.sdk.android.integrations.Hook
import com.launchdarkly.sdk.android.integrations.IdentifySeriesContext
import com.launchdarkly.sdk.android.integrations.IdentifySeriesResult
import java.util.UUID

/**
 * Delegate interface for observability hook callbacks.
 * [ObservabilityHookExporter] implements this so the hook
 * stays decoupled from the tracing / logging implementation.
 */
interface ObservabilityHookExporting {
    fun beforeEvaluation(evaluationId: String, flagKey: String, contextKey: String)
    fun afterEvaluation(
        evaluationId: String, flagKey: String, contextKey: String,
        valueJson: String?, variationIndex: Int?, inExperiment: Boolean?
    )
    fun afterIdentify(contextKeys: Map<String, String>, canonicalKey: String, completed: Boolean)
}

/**
 * Hook protocol adapter for the native Android SDK.
 * Extracts data from SDK types and delegates to [ObservabilityHookExporting].
 */
class ObservabilityHook internal constructor() : Hook(HOOK_NAME) {
    @Volatile
    var delegate: ObservabilityHookExporting? = null

    override fun beforeEvaluation(
        seriesContext: EvaluationSeriesContext,
        seriesData: Map<String, Any>
    ): Map<String, Any> {
        val evalId = UUID.randomUUID().toString()
        delegate?.beforeEvaluation(
            evaluationId = evalId,
            flagKey = seriesContext.flagKey,
            contextKey = seriesContext.context.fullyQualifiedKey
        )
        return HashMap(seriesData).apply {
            this[ObservabilityHookExporter.DATA_KEY_EVAL_ID] = evalId
        }
    }

    override fun afterEvaluation(
        seriesContext: EvaluationSeriesContext,
        seriesData: Map<String, Any>,
        evaluationDetail: EvaluationDetail<LDValue>
    ): Map<String, Any> {
        val evalId = seriesData[ObservabilityHookExporter.DATA_KEY_EVAL_ID] as? String
            ?: return seriesData

        val valueJson = evaluationDetail.value.toJsonString()
        val variationIndex = if (evaluationDetail.variationIndex != EvaluationDetail.NO_VARIATION)
            evaluationDetail.variationIndex else null
        val inExperiment = evaluationDetail.reason?.isInExperiment

        delegate?.afterEvaluation(
            evaluationId = evalId,
            flagKey = seriesContext.flagKey,
            contextKey = seriesContext.context.fullyQualifiedKey,
            valueJson = valueJson,
            variationIndex = variationIndex,
            inExperiment = inExperiment
        )
        return seriesData
    }

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

        delegate?.afterIdentify(
            contextKeys = contextKeys,
            canonicalKey = context.fullyQualifiedKey,
            completed = result.status == IdentifySeriesResult.IdentifySeriesStatus.COMPLETED
        )
        return seriesData
    }

    companion object {
        const val HOOK_NAME = "Observability Hook"
    }
}
