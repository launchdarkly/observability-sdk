package com.launchdarkly.LDNative

import com.launchdarkly.observability.plugin.ObservabilityHookProxy as PluginObservabilityHookProxy

/**
 * Bindable wrapper around the real observability hook proxy.
 *
 * Keeping this class in the LDNative package ensures Xamarin binding generation
 * emits a C# type without needing manual JNI glue code.
 */
class RealObservabilityHookProxy internal constructor(
    private val delegate: PluginObservabilityHookProxy
) {
    fun beforeEvaluation(evaluationId: String, flagKey: String, contextKey: String) {
        delegate.beforeEvaluation(evaluationId, flagKey, contextKey)
    }

    fun afterEvaluation(
        evaluationId: String,
        flagKey: String,
        contextKey: String,
        valueJson: String,
        variationIndex: Int,
        reasonJson: String?
    ) {
        delegate.afterEvaluation(evaluationId, flagKey, contextKey, valueJson, variationIndex, reasonJson)
    }

    fun afterIdentify(contextKeys: Map<String, String>, canonicalKey: String, completed: Boolean) {
        delegate.afterIdentify(contextKeys, canonicalKey, completed)
    }
}
