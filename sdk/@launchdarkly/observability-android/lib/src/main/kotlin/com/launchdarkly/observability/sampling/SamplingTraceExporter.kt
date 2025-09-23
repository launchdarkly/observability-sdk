package com.launchdarkly.observability.sampling

import io.opentelemetry.exporter.otlp.http.logs.OtlpHttpLogRecordExporter
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter
import io.opentelemetry.sdk.common.CompletableResultCode
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
        val sampledItems = sampleSpans(spans.toList(), sampler)
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
