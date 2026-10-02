package com.launchdarkly.observability.replay.exporter

import com.launchdarkly.observability.replay.transport.EventExporting
import com.launchdarkly.observability.replay.transport.EventQueueItemPayload

/**
 * Session-replay queue item for a click, rendered as an rrweb `Click` custom event.
 *
 * Mirrors the web SDK, where each click emits `addCustomEvent('Click', ...)`. Fed by Observability's
 * click funnel, so it covers automatically detected taps as well as clicks reported through
 * `LDObserve.trackClick` - the path used by embedders such as Flutter, whose UI is a single native
 * view that no native hit-test can look inside.
 *
 * @property target Element class name (web: full CSS selector path).
 * @property text The element's visible text (web: `target.textContent`).
 * @property id Stable element identifier, preferred for `clickSelector` (web: `#id`).
 * @property screenId Stable id of the screen the click landed on.
 * @property screenName Human-readable name of that screen.
 * @property sessionId The replay session this event belongs to.
 */
data class ClickItemPayload(
    val target: String?,
    val text: String?,
    val id: String?,
    val screenId: String?,
    val screenName: String?,
    override val timestamp: Long,
    val sessionId: String?
) : EventQueueItemPayload {

    override val exporterClass: Class<out EventExporting>
        get() = SessionReplayExporter::class.java

    override fun cost(): Int = 100
}
