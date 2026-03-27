package com.launchdarkly.observability.bridge

import com.launchdarkly.observability.plugin.ObservabilityHookExporter

/**
 * JVM adapter for the C# / MAUI bridge.
 *
 * Accepts simple JVM types (String, Int, HashMap) and delegates
 * to [com.launchdarkly.observability.plugin.ObservabilityHookExporter] so the tracing logic is written once.
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
        exporter.afterEvaluation(evaluationId, flagKey, contextKey, valueJson, variationIndex, reasonJson)
    }

    fun afterIdentify(contextKeys: Map<String, String>, canonicalKey: String, completed: Boolean) {
        exporter.afterIdentify(contextKeys, canonicalKey, completed)
    }
}