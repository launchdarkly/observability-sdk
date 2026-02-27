package com.launchdarkly.observability.plugin

import org.json.JSONObject

/**
 * JVM adapter for the C# / MAUI bridge.
 *
 * Accepts simple JVM types (String, Int, HashMap) and delegates
 * to [ObservabilityHookExporter] so the tracing logic is written once.
 * The C# NativeHookProxy delegates here via the Xamarin.Android binding.
 */
class ObservabilityHookProxy internal constructor(
    private val exporter: ObservabilityHookExporter
) {
    fun beforeEvaluation(evaluationId: String, flagKey: String, contextKey: String) {
        exporter.beforeEvaluation(evaluationId, flagKey, contextKey)
    }

    fun afterEvaluation(
        evaluationId: String,
        flagKey: String,
        contextKey: String,
        valueJson: String,
        variationIndex: Int,
        reasonJson: String?
    ) {
        val normalizedIndex = if (variationIndex >= 0) variationIndex else null
        exporter.afterEvaluation(
            evaluationId = evaluationId,
            flagKey = flagKey,
            contextKey = contextKey,
            valueJson = valueJson,
            variationIndex = normalizedIndex,
            inExperiment = parseInExperiment(reasonJson)
        )
    }

    fun afterIdentify(contextKeys: Map<String, String>, canonicalKey: String, completed: Boolean) {
        exporter.afterIdentify(contextKeys, canonicalKey, completed)
    }

    private fun parseInExperiment(reasonJson: String?): Boolean? {
        if (reasonJson == null) return null
        return try {
            val json = JSONObject(reasonJson)
            if (json.has("inExperiment")) json.getBoolean("inExperiment") else null
        } catch (_: Exception) {
            null
        }
    }
}
