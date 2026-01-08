package com.launchdarkly.observability.replay.exporter

import com.launchdarkly.observability.replay.InteractionEvent
import com.launchdarkly.observability.replay.transport.EventExporting
import com.launchdarkly.observability.replay.transport.EventQueueItemPayload

data class InteractionItemPayload(
    val interaction: InteractionEvent,
) : EventQueueItemPayload {

    override val timestamp: Long
        get() = interaction.positions.lastOrNull()?.timestamp ?: 0L

    override val exporterClass: Class<out EventExporting>
        get() = SessionReplayExporter::class.java

    /**
     * Queue cost heuristic: each interaction adds a fixed 300 cost units.
     */
    override fun cost(): Int = 300
}
