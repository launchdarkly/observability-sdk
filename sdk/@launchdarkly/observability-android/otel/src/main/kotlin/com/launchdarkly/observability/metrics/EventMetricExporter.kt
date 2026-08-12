package com.launchdarkly.observability.metrics

import com.launchdarkly.observability.replay.transport.BatchWorker
import com.launchdarkly.observability.replay.transport.EventQueue
import io.opentelemetry.sdk.common.CompletableResultCode
import io.opentelemetry.sdk.metrics.InstrumentType
import io.opentelemetry.sdk.metrics.data.AggregationTemporality
import io.opentelemetry.sdk.metrics.data.MetricData
import io.opentelemetry.sdk.metrics.export.AggregationTemporalitySelector
import io.opentelemetry.sdk.metrics.export.MetricExporter

/**
 * A [MetricExporter] that drops the collected [MetricData] into a shared [EventQueue] wrapped as
 * [MetricItemPayload]s, instead of exporting directly. Downstream, a [BatchWorker] hands the
 * batched payloads to [OtlpMetricExporter].
 *
 * Mirrors the Swift `OtlpMetricScheduleExporter`.
 *
 * @param eventQueue queue that receives the payloads.
 * @param temporalitySelector aggregation temporality policy, defaulting to
 *   [AggregationTemporalitySelector.deltaPreferred] (same as the previous OTLP exporter).
 * @param batchWorker optional worker used to satisfy [flush]; when set, flush drains the queue.
 * @param clock supplies the wall-clock timestamp stamped on each payload (for queue ordering).
 */
internal class EventMetricExporter(
    private val eventQueue: EventQueue,
    private val temporalitySelector: AggregationTemporalitySelector = AggregationTemporalitySelector.deltaPreferred(),
    private val batchWorker: BatchWorker? = null,
    private val clock: () -> Long = { System.currentTimeMillis() },
) : MetricExporter {

    override fun export(metrics: Collection<MetricData>): CompletableResultCode {
        if (metrics.isEmpty()) return CompletableResultCode.ofSuccess()
        val timestamp = clock()
        val payloads = metrics.map { MetricItemPayload(it, timestamp = timestamp) }
        eventQueue.send(payloads)
        return CompletableResultCode.ofSuccess()
    }

    override fun flush(): CompletableResultCode {
        batchWorker?.flush()
        return CompletableResultCode.ofSuccess()
    }

    override fun shutdown(): CompletableResultCode {
        batchWorker?.flush()
        return CompletableResultCode.ofSuccess()
    }

    override fun getAggregationTemporality(instrumentType: InstrumentType): AggregationTemporality =
        temporalitySelector.getAggregationTemporality(instrumentType)
}
