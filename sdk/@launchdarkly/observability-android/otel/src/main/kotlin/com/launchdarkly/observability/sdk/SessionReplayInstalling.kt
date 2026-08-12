package com.launchdarkly.observability.sdk

import com.launchdarkly.observability.client.ObservabilityContext
import com.launchdarkly.observability.context.LDObserveContext

/**
 * Marker for Session Replay configuration, implemented by `ReplayOptions` in the full observability
 * artifact.
 *
 * Session Replay and its privacy model live in the full artifact, so the OTel-only core cannot name
 * `ReplayOptions` directly. Accepting this marker in [LDObserve.init] keeps that call site
 * source-compatible — callers still pass a `ReplayOptions` — without dragging the replay
 * implementation into the OTel-only product.
 */
interface ReplayConfiguration

/** Marker for a Session Replay image capture implementation, implemented by `ImageCaptureServicing`. */
interface ImageCapturing

/**
 * Installs Session Replay on behalf of [LDObserve.init].
 *
 * Supplied by the full artifact through [ObservabilityExtensions]; when only the OTel-only artifact
 * is present there is no implementation, and asking for replay logs an explanatory error.
 */
interface SessionReplayInstalling {
    fun install(
        replay: ReplayConfiguration,
        obsContext: ObservabilityContext,
        ldContext: LDObserveContext,
        imageCapture: ImageCapturing?,
    )
}
