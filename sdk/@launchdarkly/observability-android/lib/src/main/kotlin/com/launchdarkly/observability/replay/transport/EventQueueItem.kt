package com.launchdarkly.observability.replay.transport

data class EventQueueItem(
    val payload: EventQueueItemPayload,
) {
    val cost: Int = payload.cost()
    val exporterClass: Class<out EventExporting> = payload.exporterClass
    val timestamp: Long
        get() = payload.timestamp
}
