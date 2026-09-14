package com.launchdarkly.observability.client

/**
 * A click/tap broadcast to in-process consumers such as Session Replay.
 *
 * Emitted by the single click emitter in [ObservabilityService.emitClick] for every click path —
 * automatic tap detection and the manual [com.launchdarkly.observability.sdk.LDObserve.trackClick]
 * API. Session Replay maps these to RRWeb `Custom` events tagged `"Click"`, mirroring the web SDK
 * where each click emits `addCustomEvent('Click', ...)`.
 *
 * Embedders that render their own UI into a single native view (Flutter, which draws everything
 * into a `FlutterSurfaceView`) resolve the target in their own widget tree and report it through
 * `trackClick`, so the same funnel serves both native and embedder-owned taps.
 *
 * @property tag Short element tag, e.g. `Button`.
 * @property classname Fully-qualified element class name, when known.
 * @property id Stable element identifier (`ldId`, resource entry name, React Native `testID`).
 * @property text Visible text/label of the element, when known and not sensitive.
 * @property xpath View path of the element, when known.
 * @property screenId Stable id of the screen the click landed on.
 * @property screenName Human-readable name of that screen, matching `screen_view.event.name`.
 * @property x Click x coordinate in screen pixels.
 * @property y Click y coordinate in screen pixels.
 * @property timestamp Capture time, in milliseconds since epoch.
 */
data class ClickEvent(
    val tag: String? = null,
    val classname: String? = null,
    val id: String? = null,
    val text: String? = null,
    val xpath: String? = null,
    val screenId: String? = null,
    val screenName: String? = null,
    val x: Long? = null,
    val y: Long? = null,
    val timestamp: Long = System.currentTimeMillis(),
)
