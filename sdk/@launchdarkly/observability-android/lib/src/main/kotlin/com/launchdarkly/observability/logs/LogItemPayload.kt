package com.launchdarkly.observability.logs

import com.launchdarkly.observability.replay.transport.EventExporting
import com.launchdarkly.observability.replay.transport.EventQueueItemPayload
import io.opentelemetry.sdk.logs.data.LogRecordData

/**
 * Queue payload for a single OpenTelemetry [LogRecordData]. Mirrors the Swift `LogItem`.
 */
data class LogItemPayload(
    val logRecord: LogRecordData,
) : EventQueueItemPayload {

    override val exporterClass: Class<out EventExporting>
        get() = OtlpLogExporter::class.java

    /** Timestamp (ms since epoch) used for queue ordering. */
    override val timestamp: Long
        get() = logRecord.timestampEpochNanos / 1_000_000L

    /**
     * Queue cost heuristic: a fixed per-record base plus an attribute-count contribution,
     * matching the Swift `LogItem.cost()` formula (`300 + attributes.count * 100`).
     */
    override fun cost(): Int = BASE_COST + logRecord.attributes.size() * PER_ATTRIBUTE_COST

    private companion object {
        const val BASE_COST = 300
        const val PER_ATTRIBUTE_COST = 100
    }
}
