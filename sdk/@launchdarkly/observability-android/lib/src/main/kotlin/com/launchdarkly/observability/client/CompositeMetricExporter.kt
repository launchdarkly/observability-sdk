package com.launchdarkly.observability.client

import io.opentelemetry.sdk.common.CompletableResultCode
import io.opentelemetry.sdk.metrics.InstrumentType
import io.opentelemetry.sdk.metrics.data.AggregationTemporality
import io.opentelemetry.sdk.metrics.data.MetricData
import io.opentelemetry.sdk.metrics.export.MetricExporter

/**
 * A composite metric exporter that forwards metrics to multiple underlying exporters.
 *
 * This allows sending the same metrics to multiple destinations (e.g., OTLP endpoint
 * and local debug output) without duplicating the sampling logic. All operations
 * (export, flush, shutdown) are forwarded to all underlying exporters.
 *
 * The composite operation succeeds only if ALL underlying exporters succeed.
 *
 * @param exporters The list of underlying exporters to forward operations to
 */
class CompositeMetricExporter(
    private val exporters: List<MetricExporter>
) : MetricExporter {

    /**
     * Convenience constructor that accepts a variable number of exporters.
     *
     * @param exporters The exporters to compose
     */
    constructor(vararg exporters: MetricExporter) : this(exporters.toList())

    /**
     * Exports metrics to all underlying exporters.
     *
     * @param metrics The metrics to export
     * @return A CompletableResultCode that succeeds only if all underlying exports succeed
     */
    override fun export(metrics: Collection<MetricData>): CompletableResultCode {
        val results = exporters.map { exporter ->
            exporter.export(metrics)
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

    /**
     * Returns the aggregation temporality for the given instrument type.
     *
     * For composite exporters, this returns the temporality from the first exporter.
     * Note: All underlying exporters should ideally use the same temporality for consistency.
     *
     * @param instrumentType The instrument type to get temporality for
     * @return The aggregation temporality, or null if no exporters are configured
     */
    override fun getAggregationTemporality(instrumentType: InstrumentType): AggregationTemporality? {
        return exporters.firstOrNull()?.getAggregationTemporality(instrumentType)
    }
}