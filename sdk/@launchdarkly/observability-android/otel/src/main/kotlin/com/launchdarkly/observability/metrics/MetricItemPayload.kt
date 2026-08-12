package com.launchdarkly.observability.metrics

import com.launchdarkly.observability.replay.transport.EventExporting
import com.launchdarkly.observability.replay.transport.EventQueueItemPayload
import io.opentelemetry.sdk.metrics.data.MetricData

/**
 * Queue payload for a single OpenTelemetry [MetricData]. Mirrors the Swift `MetricItem`.
 *
 * Metric payloads carry an explicit [timestamp] because `MetricData` itself does not expose a
 * collection timestamp (the one on `PointData` is per-point). [EventMetricExporter] supplies the
 * export wall-clock time when it enqueues these payloads.
 */
data class MetricItemPayload(
    val metricData: MetricData,
    override val timestamp: Long,
) : EventQueueItemPayload {

    override val exporterClass: Class<out EventExporting>
        get() = OtlpMetricExporter::class.java

    /**
     * Queue cost heuristic: a fixed per-metric base plus a contribution per data point, matching
     * the Swift `MetricItem.cost()` formula (`300 + points.count * 100`).
     */
    override fun cost(): Int = BASE_COST + metricData.data.points.size * PER_POINT_COST

    private companion object {
        const val BASE_COST = 300
        const val PER_POINT_COST = 100
    }
}
