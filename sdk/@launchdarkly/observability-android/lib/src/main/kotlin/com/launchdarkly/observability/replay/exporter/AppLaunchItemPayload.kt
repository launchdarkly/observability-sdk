package com.launchdarkly.observability.replay.exporter

import com.launchdarkly.observability.replay.RRWebCustomDataTag
import com.launchdarkly.observability.replay.transport.EventExporting
import com.launchdarkly.observability.replay.transport.EventQueueItemPayload

/**
 * Session-replay queue item for an app launch, rendered as an rrweb `Launch` custom event.
 *
 * Mirrors the analytics taxonomy `app_launch` event: each process launch emits a breadcrumb on the
 * active recording, independent of the corresponding `app_launch` span.
 *
 * @property launchType The product launch type (`install` / `update` / `relaunch`).
 * @property sessionId The replay session this event belongs to.
 */
data class AppLaunchItemPayload(
    val launchType: String?,
    override val timestamp: Long,
    val sessionId: String?
) : EventQueueItemPayload {

    val tag: RRWebCustomDataTag = RRWebCustomDataTag.APP_LAUNCH

    override val exporterClass: Class<out EventExporting>
        get() = SessionReplayExporter::class.java

    override fun cost(): Int = 100
}
