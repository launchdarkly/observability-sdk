package com.launchdarkly.observability.sampling

import io.opentelemetry.sdk.common.CompletableResultCode
import io.opentelemetry.sdk.logs.data.LogRecordData
import io.opentelemetry.sdk.logs.export.LogRecordExporter

/**
 * A composite log exporter that forwards log records to multiple underlying exporters.
 * 
 * This allows sending the same log records to multiple destinations (e.g., OTLP endpoint 
 * and local debug output) without duplicating the sampling logic. All operations 
 * (export, flush, shutdown) are forwarded to all underlying exporters.
 * 
 * The composite operation succeeds only if ALL underlying exporters succeed.
 * 
 * @param exporters The list of underlying exporters to forward operations to
 */
class CompositeLogExporter(
    private val exporters: List<LogRecordExporter>
) : LogRecordExporter {
    
    /**
     * Convenience constructor that accepts a variable number of exporters.
     * 
     * @param exporters The exporters to compose
     */
    constructor(vararg exporters: LogRecordExporter) : this(exporters.toList())
    
    /**
     * Exports log records to all underlying exporters.
     * 
     * @param logRecords The log records to export
     * @return A CompletableResultCode that succeeds only if all underlying exports succeed
     */
    override fun export(logRecords: Collection<LogRecordData>): CompletableResultCode {
        val results = exporters.map { exporter ->
            exporter.export(logRecords)
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
