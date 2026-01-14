package com.launchdarkly.observability.replay.transport

/**
 * Base payload type for items enqueued into the replay transport queue.
 */
interface EventQueueItemPayload {
    /**
     * Heuristic cost used for queue limits and batching decisions, measured in cost units, not bytes.
     */
    fun cost(): Int

    /**
     * Timestamp (ms since epoch) used for ordering items in the queue.
     */
    val timestamp: Long

    /**
     * Exporter class responsible for handling this payload.
     */
    val exporterClass: Class<out EventExporting>
}
