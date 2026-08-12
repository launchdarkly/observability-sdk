package com.launchdarkly.observability.traces

import com.launchdarkly.observability.bridge.BRIDGE_SPAN_ID_ATTRIBUTE_KEY
import com.launchdarkly.observability.bridge.BRIDGE_TRACE_ID_ATTRIBUTE_KEY
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.SpanContext
import io.opentelemetry.sdk.trace.data.DelegatingSpanData
import io.opentelemetry.sdk.trace.data.SpanData

private val bridgeTraceIdKey = AttributeKey.stringKey(BRIDGE_TRACE_ID_ATTRIBUTE_KEY)
private val bridgeSpanIdKey = AttributeKey.stringKey(BRIDGE_SPAN_ID_ATTRIBUTE_KEY)

/**
 * If [data] carries bridge-supplied IDs (set by the ReactNative / Objective-C bridges),
 * replace the auto-generated IDs with them and strip the internal attributes.
 *
 * Extracted from the previous `SamplingTraceExporter` so it can be shared by the new
 * [EventSpanProcessor].
 */
internal fun applyBridgeIdOverrides(data: SpanData): SpanData {
    val overrideTraceId = data.attributes[bridgeTraceIdKey]
    val overrideSpanId = data.attributes[bridgeSpanIdKey]
    if (overrideTraceId == null && overrideSpanId == null) return data

    val original = data.spanContext
    val overriddenContext = SpanContext.create(
        overrideTraceId ?: original.traceId,
        overrideSpanId ?: original.spanId,
        original.traceFlags,
        original.traceState,
    )

    val filteredAttributes = Attributes.builder().apply {
        data.attributes.forEach { key, value ->
            if (key != bridgeTraceIdKey && key != bridgeSpanIdKey) {
                @Suppress("UNCHECKED_CAST")
                put(key as AttributeKey<Any>, value)
            }
        }
    }.build()

    return object : DelegatingSpanData(data) {
        override fun getSpanContext(): SpanContext = overriddenContext
        override fun getAttributes(): Attributes = filteredAttributes
    }
}
