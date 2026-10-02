package com.launchdarkly.observability.replay.exporter

import com.launchdarkly.observability.replay.RRWebCustomDataTag
import com.launchdarkly.observability.replay.transport.EventExporting
import com.launchdarkly.observability.replay.transport.EventQueueItemPayload

/**
 * Session-replay queue item for an app-lifecycle transition, rendered as an rrweb `Foreground` or
 * `Background` custom event.
 *
 * Mirrors the analytics taxonomy app-lifecycle events: each foreground/background transition emits a
 * breadcrumb on the active recording, independent of the corresponding `app_foreground` /
 * `app_background` span.
 *
 * @property tag The rrweb custom tag ([RRWebCustomDataTag.APP_FOREGROUND] / [RRWebCustomDataTag.APP_BACKGROUND]).
 * @property lifecycleState The OTel-aligned lifecycle state (e.g. `foreground`, `background`).
 * @property sessionId The replay session this event belongs to.
 */
data class AppLifecycleItemPayload(
    val tag: RRWebCustomDataTag,
    val lifecycleState: String?,
    override val timestamp: Long,
    val sessionId: String?
) : EventQueueItemPayload {

    override val exporterClass: Class<out EventExporting>
        get() = SessionReplayExporter::class.java

    override fun cost(): Int = 100
}
