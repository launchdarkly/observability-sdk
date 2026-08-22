package com.launchdarkly.observability.client

import io.opentelemetry.api.common.Attributes

/**
 * An identified context broadcast to in-process consumers such as Session Replay.
 *
 * Emitted by the single identify funnel in
 * [com.launchdarkly.observability.client.ObservabilityService] for every identify path —
 * `LDClient.identify` (via the observability hook) and the manual
 * [com.launchdarkly.observability.sdk.LDObserve.identify] API, including standalone init without
 * `LDClient`. Session Replay maps these to an `identifySession` call and an RRWeb `Identify` event.
 *
 * @property contextKeys Context kind -> key pairs for the identified context. A single-kind
 *   identify made through [com.launchdarkly.observability.sdk.LDObserve.identify] carries just
 *   `{"user": key}`.
 * @property canonicalKey The fully qualified context key, used as the session's user identifier.
 * @property attributes Caller-supplied identity attributes, if any. Unlike [contextKeys] these are
 *   not stamped onto later spans; they describe the identity itself.
 * @property timestamp Identify time, in milliseconds since epoch.
 */
data class IdentifyEvent(
    val contextKeys: Map<String, String>,
    val canonicalKey: String,
    val attributes: Attributes,
    val timestamp: Long = System.currentTimeMillis(),
)
