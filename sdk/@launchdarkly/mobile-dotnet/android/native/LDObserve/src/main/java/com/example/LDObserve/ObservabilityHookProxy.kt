package com.launchdarkly.LDNative

import com.launchdarkly.observability.plugin.ObservabilityHookProxy as RealProxy

/**
 * Thin forwarding wrapper so the Xamarin.Android binding can see this class.
 *
 * The real [RealProxy] lives in observability-android and shares
 * [com.launchdarkly.observability.plugin.ObservabilityHookExporter] with
 * [com.launchdarkly.observability.plugin.ObservabilityHook].
 * The binding generator only emits C# wrappers for classes in *this* module's
 * package, so we re-expose every method here.
 */
class ObservabilityHookProxy internal constructor(
    private val delegate: RealProxy
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
