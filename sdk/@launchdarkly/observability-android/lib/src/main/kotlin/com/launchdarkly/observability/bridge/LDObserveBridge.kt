package com.launchdarkly.observability.bridge

import com.launchdarkly.observability.sdk.LDObserve

object LDObserveBridge {
    fun getObservabilityHookProxy(): ObservabilityHookProxy? {
        return LDObserve.observabilityClient?.let { ObservabilityHookProxy(it.hookExporter) }
    }

    fun getKotlinTracer(): KotlinTracer? {
        return LDObserve.observabilityClient?.let { KotlinTracer(it.getTracer()) }
    }

    fun getKotlinLogger(): KotlinLogger? {
        return LDObserve.observabilityClient?.let {
            KotlinLogger(internalLogger = it.getLogger(), customerLogger = it)
        }
    }

    /**
     * Records a custom `track` event. Always broadcasts a Session Replay `Track`
     * timeline event and, when `analytics.trackEvents` is enabled, emits the
     * `track` span. [data] carries the optional event payload as a plain map
     * (e.g. across the Flutter pigeon bridge), so callers need not depend on the
     * LaunchDarkly model types.
     */
    fun track(key: String, data: Map<String, Any?>?, metricValue: Double?) {
        LDObserve.track(key, data, metricValue)
    }
}
