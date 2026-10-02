package com.launchdarkly.observability.bridge

import com.launchdarkly.observability.sdk.LDObserve
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes

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
     * LaunchDarkly model types. [contextKeys] carries the evaluation context's
     * kind -> key pairs; when supplied they annotate the `track` span (not the
     * Session Replay `Track` payload), so hosts whose LaunchDarkly client lives
     * outside this SDK (e.g. Flutter) can attribute the span to the same context
     * the web SDK records.
     */
    fun track(
        key: String,
        data: Map<String, Any?>?,
        metricValue: Double?,
        contextKeys: Map<String, String>?,
    ) {
        val service = LDObserve.observabilityClient
        if (contextKeys.isNullOrEmpty() || service == null) {
            // No explicit context (or no live service): the public path uses the
            // cached identify keys for the span.
            LDObserve.track(key, data, metricValue)
            return
        }
        val contextKeyBuilder = Attributes.builder()
        for ((kind, value) in contextKeys) {
            contextKeyBuilder.put(AttributeKey.stringKey(kind), value)
        }
        service.track(
            key,
            metricValue,
            data?.toOtelAttributes() ?: Attributes.empty(),
            contextKeyBuilder.build(),
        )
    }
}
