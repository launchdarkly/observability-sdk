package com.launchdarkly.observability.sampling

import com.launchdarkly.observability.bridge.BRIDGE_SPAN_ID_ATTRIBUTE_KEY
import com.launchdarkly.observability.bridge.BRIDGE_TRACE_ID_ATTRIBUTE_KEY
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.SpanContext
import io.opentelemetry.exporter.otlp.http.logs.OtlpHttpLogRecordExporter
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter
import io.opentelemetry.sdk.common.CompletableResultCode
import io.opentelemetry.sdk.trace.data.DelegatingSpanData
import io.opentelemetry.sdk.trace.data.SpanData
import io.opentelemetry.sdk.trace.export.SpanExporter

/**
 * A [SpanExporter] that applies sampling logic before delegating to an [OtlpHttpLogRecordExporter].
 *
 * This exporter wraps an [OtlpHttpSpanExporter] and uses a [ExportSampler] to determine which
 * spans should be exported based on configurable sampling rules. Spans that don't match the
 * sampling criteria are filtered out, reducing the volume of telemetry data sent to the
 * observability backend.
 *
 * @param delegate The underlying [OtlpHttpSpanExporter] that handles the actual export
 * @param sampler The custom sampler that determines which spans to export
 */
class SamplingTraceExporter(
    private val delegate: SpanExporter,
    private val sampler: ExportSampler
) : SpanExporter {

    /**
     * Exports spans after applying sampling logic.
     *
     * This method filters the provided spans using the configured sampler,
     * then delegates the export of sampled spans to the underlying OTLP exporter.
     * If no spans pass the sampling criteria, the export is considered successful
     * without sending any data.
     *
     * @param spans The collection of spans to potentially export
     * @return A [CompletableResultCode] indicating the success or failure of the export operation
     */
    override fun export(spans: Collection<SpanData>): CompletableResultCode {
        val processed = spans.map { applyBridgeIdOverrides(it) }
        val sampledItems = sampleSpans(processed, sampler)
        if (sampledItems.isEmpty()) {
            return CompletableResultCode.ofSuccess()
        }
        return delegate.export(sampledItems)
    }

    /**
     * Flushes any pending spans in the underlying exporter.
     *
     * @return A [CompletableResultCode] indicating the success or failure of the flush operation
     */
    override fun flush(): CompletableResultCode {
        return delegate.flush()
    }

    /**
     * Shuts down the exporter and releases any resources.
     *
     * @return A [CompletableResultCode] indicating the success or failure of the shutdown operation
     */
    override fun shutdown(): CompletableResultCode {
        return delegate.shutdown()
    }
}

private val bridgeTraceIdKey = AttributeKey.stringKey(BRIDGE_TRACE_ID_ATTRIBUTE_KEY)
private val bridgeSpanIdKey = AttributeKey.stringKey(BRIDGE_SPAN_ID_ATTRIBUTE_KEY)

/**
 * If the span carries bridge-supplied IDs (set by [KotlinTracer]),
 * replaces the auto-generated IDs with them and strips the internal attributes.
 */
private fun applyBridgeIdOverrides(data: SpanData): SpanData {
    val overrideTraceId = data.attributes[bridgeTraceIdKey]
    val overrideSpanId = data.attributes[bridgeSpanIdKey]
    if (overrideTraceId == null && overrideSpanId == null) return data

    val original = data.spanContext
    val overriddenContext = SpanContext.create(
        overrideTraceId ?: original.traceId,
        overrideSpanId ?: original.spanId,
        original.traceFlags,
        original.traceState
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
