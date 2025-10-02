package com.launchdarkly.observability.client

import io.opentelemetry.sdk.common.CompletableResultCode
import io.opentelemetry.sdk.trace.data.SpanData
import io.opentelemetry.sdk.trace.export.SpanExporter

/**
 * A span exporter that conditionally forwards spans based on their source.
 * This allows different filtering rules for error spans vs normal spans.
 *
 * @property delegate The underlying exporter to forward spans to
 * @property allowNormalSpans Whether to allow normal application spans
 * @property allowErrorSpans Whether to allow error spans created by recordError method
 */
class ConditionalSpanExporter(
    private val delegate: SpanExporter,
    private val allowNormalSpans: Boolean,
    private val allowErrorSpans: Boolean
) : SpanExporter {
    
    override fun export(spans: Collection<SpanData>): CompletableResultCode {
        val filteredSpans = spans.filter { spanData ->
            // Check if this is an error span created by recordError method
            val spanName = spanData.name
            val isErrorSpan = spanName == InstrumentationManager.ERROR_SPAN_NAME
            
            when {
                isErrorSpan -> allowErrorSpans
                else -> allowNormalSpans
            }
        }
        
        return if (filteredSpans.isNotEmpty()) {
            delegate.export(filteredSpans)
        } else {
            CompletableResultCode.ofSuccess()
        }
    }
    
    override fun flush(): CompletableResultCode = delegate.flush()
    
    override fun shutdown(): CompletableResultCode = delegate.shutdown()
}