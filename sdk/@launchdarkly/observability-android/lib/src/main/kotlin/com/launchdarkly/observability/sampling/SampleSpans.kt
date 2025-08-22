package com.launchdarkly.observability.sampling

import io.opentelemetry.api.common.Attributes
import io.opentelemetry.sdk.common.InstrumentationScopeInfo
import io.opentelemetry.sdk.trace.data.SpanData

fun sampleSpans(
    items: List<SpanData>,
    sampler: ExportSampler
): List<SpanData> {

    if (!sampler.isSamplingEnabled()) {
        return items
    }

    val omittedSpansIds = ArrayDeque<String>() // ArrayDeque prevents O(n²) complexity caused by removing the first element in the while loop.
    val spanById = mutableMapOf<String, SpanData>()
    val childrenByParentId = mutableMapOf<String, MutableList<String>>()

    // The first pass we sample items which are directly impacted by a sampling decision.
    // We also build a map of children spans by parent span id, which allows us to quickly traverse the span tree.
    for (item in items) {
        item.parentSpanContext?.spanId?.let { parentSpanId ->
            childrenByParentId.getOrPut(parentSpanId) { mutableListOf() }
                .add(item.spanContext.spanId)
        }
        val sampleResult = sampler.sampleSpan(item)
        if (sampleResult.sample) {
            if (sampleResult.attributes != null) {
                spanById[item.spanContext.spanId] = cloneSpanDataWithAttributes(item, sampleResult.attributes)
            } else {
                spanById[item.spanContext.spanId] = item
            }
        } else {
            omittedSpansIds.addLast(item.spanContext.spanId)
        }
    }

    // Find all children of spans that have been sampled out and remove them.
    // Repeat until there are no more children to remove.
    while (omittedSpansIds.isNotEmpty()) {
        val spanId = omittedSpansIds.removeFirst()
        val affectedSpans = childrenByParentId[spanId] ?: continue

        for (spanIdToRemove in affectedSpans) {
            spanById.remove(spanIdToRemove)
            omittedSpansIds.addLast(spanIdToRemove)
        }
    }
    return spanById.values.toList()
}

private fun cloneSpanDataWithAttributes(
    span: SpanData,
    attributes: Attributes
): SpanData {
    return object : SpanData by span {
        override fun getTraceId(): String? = span.traceId
        override fun getSpanId(): String? = span.spanId
        override fun getParentSpanId(): String? = span.parentSpanId
        override fun getInstrumentationScopeInfo(): InstrumentationScopeInfo? = span.instrumentationScopeInfo

        override fun getAttributes(): Attributes {
            return Attributes.builder().apply {
                putAll(span.attributes)
                putAll(attributes)
            }.build()
        }
    }
}
