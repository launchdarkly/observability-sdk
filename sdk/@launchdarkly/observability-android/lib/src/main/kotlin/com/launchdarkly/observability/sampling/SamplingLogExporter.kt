package com.launchdarkly.observability.sampling

import io.opentelemetry.exporter.otlp.http.logs.OtlpHttpLogRecordExporter
import io.opentelemetry.sdk.common.CompletableResultCode
import io.opentelemetry.sdk.logs.data.LogRecordData
import io.opentelemetry.sdk.logs.export.LogRecordExporter

/**
 * A [LogRecordExporter] that applies sampling logic before delegating to an [OtlpHttpLogRecordExporter].
 *
 * This exporter wraps an [OtlpHttpLogRecordExporter] and uses a [CustomSampler] to determine which
 * log records should be exported based on configurable sampling rules. Log records that don't
 * match the sampling criteria are filtered out, reducing the volume of telemetry data sent to
 * the observability backend.
 *
 * @param delegate The underlying [OtlpHttpLogRecordExporter] that handles the actual export
 * @param sampler The custom sampler that determines which log records to export
 */
class SamplingLogExporter(
    private val delegate: OtlpHttpLogRecordExporter,
    private val sampler: CustomSampler
) : LogRecordExporter {

    /**
     * Exports log records after applying sampling logic.
     *
     * This method filters the provided log records using the configured sampler,
     * then delegates the export of sampled records to the underlying [OtlpHttpLogRecordExporter].
     * If no log records pass the sampling criteria, the export is considered successful without sending any data.
     *
     * @param logs The collection of log records to potentially export
     * @return A [CompletableResultCode] indicating the success or failure of the export operation
     */
    override fun export(logs: Collection<LogRecordData>): CompletableResultCode {
        val sampledItems = sampleLogs(logs.toList(), sampler)
        if (sampledItems.isEmpty()) {
            return CompletableResultCode.ofSuccess()
        }
        return delegate.export(sampledItems)
    }

    /**
     * Flushes any pending log records in the underlying exporter.
     *
     * @return A [CompletableResultCode] indicating the success or failure of the flush operation
     */
    override fun flush(): CompletableResultCode {
        return delegate.flush()
    }

    /**
     * Shuts down the underlying exporter and releases any resources.
     *
     * @return A [CompletableResultCode] indicating the success or failure of the shutdown operation
     */
    override fun shutdown(): CompletableResultCode {
        return delegate.shutdown()
    }
}