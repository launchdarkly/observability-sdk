package com.launchdarkly.observability.replay.transport

interface EventExporting {
    suspend fun export(items: List<EventQueueItem>)
}
