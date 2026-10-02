package com.launchdarkly.observability.traces

import com.launchdarkly.observability.replay.transport.EventExporting
import com.launchdarkly.observability.replay.transport.EventQueueItemPayload
import io.opentelemetry.sdk.trace.data.SpanData

/**
 * Queue payload for a single OpenTelemetry [SpanData]. Mirrors the Swift `SpanItem`.
 */
data class SpanItemPayload(
    val spanData: SpanData,
) : EventQueueItemPayload {

    override val exporterClass: Class<out EventExporting>
        get() = OtlpTraceExporter::class.java

    /** Timestamp (ms since epoch) used for queue ordering; taken from the span's end time. */
    override val timestamp: Long
        get() = spanData.endEpochNanos / 1_000_000L

    /**
     * Queue cost heuristic: a fixed per-span base plus a contribution for events and attributes,
     * matching the Swift `SpanItem.cost()` formula.
     */
    override fun cost(): Int =
        BASE_COST +
            spanData.events.size * PER_EVENT_COST +
            spanData.attributes.size() * PER_ATTRIBUTE_COST

    private companion object {
        const val BASE_COST = 300
        const val PER_EVENT_COST = 100
        const val PER_ATTRIBUTE_COST = 100
    }
}
