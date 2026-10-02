package com.launchdarkly.observability.client

import io.opentelemetry.api.common.Attributes

/**
 * A custom analytics `track` event broadcast to in-process consumers such as Session Replay.
 *
 * Emitted by the single `track` emitter in
 * [com.launchdarkly.observability.client.ObservabilityService] for every track path —
 * `LDClient.track` (via the observability hook) and the manual
 * [com.launchdarkly.observability.sdk.LDObserve.track] API, including standalone init without
 * `LDClient`. Session Replay maps these to RRWeb `Custom` events tagged `"Track"`.
 *
 * @property name The track event key.
 * @property metricValue Optional numeric metric value associated with the event.
 * @property attributes User-supplied track data attributes (no context keys).
 * @property timestamp Capture time, in milliseconds since epoch.
 */
data class TrackEvent(
    val name: String,
    val metricValue: Double?,
    val attributes: Attributes,
    val timestamp: Long = System.currentTimeMillis(),
)
