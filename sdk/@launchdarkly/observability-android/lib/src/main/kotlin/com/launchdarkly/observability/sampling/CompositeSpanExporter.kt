package com.launchdarkly.observability.sampling

import io.opentelemetry.sdk.common.CompletableResultCode
import io.opentelemetry.sdk.trace.data.SpanData
import io.opentelemetry.sdk.trace.export.SpanExporter

/**
 * A composite span exporter that forwards spans to multiple underlying exporters.
 * 
 * This allows sending the same spans to multiple destinations (e.g., OTLP endpoint 
 * and in-memory storage) without duplicating the sampling logic. All operations 
 * (export, flush, shutdown) are forwarded to all underlying exporters.
 * 
 * The composite operation succeeds only if ALL underlying exporters succeed.
 * 
 * @param exporters The list of underlying exporters to forward operations to
 */
class CompositeSpanExporter(
    private val exporters: List<SpanExporter>
) : SpanExporter {
    
    /**
     * Convenience constructor that accepts a variable number of exporters.
     * 
     * @param exporters The exporters to compose
     */
    constructor(vararg exporters: SpanExporter) : this(exporters.toList())
    
    /**
     * Exports spans to all underlying exporters.
     * 
     * @param spans The spans to export
     * @return A CompletableResultCode that succeeds only if all underlying exports succeed
     */
    override fun export(spans: Collection<SpanData>): CompletableResultCode {
        val results = exporters.map { exporter ->
            exporter.export(spans)
        }
        return CompletableResultCode.ofAll(results)
    }
    
    /**
     * Flushes all underlying exporters.
     * 
     * @return A CompletableResultCode that succeeds only if all underlying flushes succeed
     */
    override fun flush(): CompletableResultCode {
        val results = exporters.map { exporter ->
            exporter.flush()
        }
        return CompletableResultCode.ofAll(results)
    }
    
    /**
     * Shuts down all underlying exporters.
     * 
     * @return A CompletableResultCode that succeeds only if all underlying shutdowns succeed
     */
    override fun shutdown(): CompletableResultCode {
        val results = exporters.map { exporter ->
            exporter.shutdown()
        }
        return CompletableResultCode.ofAll(results)
    }
}