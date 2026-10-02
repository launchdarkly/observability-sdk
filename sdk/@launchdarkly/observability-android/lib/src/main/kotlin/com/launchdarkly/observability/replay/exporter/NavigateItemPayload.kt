package com.launchdarkly.observability.replay.exporter

import com.launchdarkly.observability.replay.transport.EventExporting
import com.launchdarkly.observability.replay.transport.EventQueueItemPayload

/**
 * Session-replay queue item for a screen change, rendered as an rrweb `Navigate` custom event.
 *
 * Mirrors the web SDK, where each path change emits `addCustomEvent('Navigate', url)`. On Android
 * the screen name acts as the "route", produced by Observability's screen tracking (automatic
 * Activity capture or the manual `trackScreenView` API).
 *
 * @property name The screen name (route) being navigated to.
 * @property sessionId The replay session this event belongs to.
 */
data class NavigateItemPayload(
    val name: String,
    override val timestamp: Long,
    val sessionId: String?
) : EventQueueItemPayload {

    override val exporterClass: Class<out EventExporting>
        get() = SessionReplayExporter::class.java

    override fun cost(): Int = 100
}
