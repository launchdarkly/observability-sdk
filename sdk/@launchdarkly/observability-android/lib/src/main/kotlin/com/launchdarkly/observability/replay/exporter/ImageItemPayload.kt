package com.launchdarkly.observability.replay.exporter

import com.launchdarkly.observability.replay.capture.CaptureEvent
import com.launchdarkly.observability.replay.transport.EventExporting
import com.launchdarkly.observability.replay.transport.EventQueueItemPayload

data class ImageItemPayload(
    val capture: CaptureEvent,
) : EventQueueItemPayload {

    override val timestamp: Long
        get() = capture.timestamp

    override val exporterClass: Class<out EventExporting>
        get() = SessionReplayExporter::class.java

    /**
     * Queue cost derived from the base64 string length; it is a cost unit, not exact bytes.
     */
    override fun cost(): Int = capture.imageBase64.length
}
