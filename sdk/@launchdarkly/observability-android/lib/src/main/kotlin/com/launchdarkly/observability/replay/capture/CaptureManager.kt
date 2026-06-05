package com.launchdarkly.observability.replay.capture

import com.launchdarkly.observability.context.ObserveLogger
import com.launchdarkly.observability.replay.ReplayOptions
import io.opentelemetry.android.session.SessionManager
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.concurrent.atomic.AtomicInteger

/**
 * A source of [ExportFrame]s taken from the lowest visible window. Captures
 * are emitted on the [captureFlow] property of this class.
 *
 * @param sessionManager Used to get current session for tagging [ExportFrame] with session id
 */
class CaptureManager(
    private val sessionManager: SessionManager,
    private val options: ReplayOptions,
    private val logger: ObserveLogger,
    private val imageCaptureService: ImageCaptureServicing = ImageCaptureService(options, logger),
    // TODO: O11Y-628 - add captureQuality options
) {
    private val _captureEventFlow = MutableSharedFlow<ExportFrame>()
    val captureFlow: SharedFlow<ExportFrame> = _captureEventFlow.asSharedFlow()
    private val exportDiffManager = ExportDiffManager(
        compression = options.compression,
        scale = options.scale ?: 1f,
    )
    /**
     * Base (fast) cadence derived from the configured frame rate. The effective
     * cadence is adapted at runtime between this and [maxIdleCaptureDelayMillis]
     * (see [effectiveCaptureDelayMillis]).
     */
    val captureDelayMillis: Long = if (options.frameRate > 0) {
        (1000.0 / options.frameRate).toLong().coerceAtLeast(1L)
    } else {
        Long.MAX_VALUE
    }

    // Slowest cadence used while the screen is idle (a run of identical frames).
    // A multiple of the base rate so back-off has an effect even when the base
    // interval is already >= 1s, with a hard ceiling so capture never stalls.
    private val maxIdleCaptureDelayMillis: Long = if (captureDelayMillis == Long.MAX_VALUE) {
        Long.MAX_VALUE
    } else {
        (captureDelayMillis * 8).coerceAtMost(MAX_IDLE_CEILING_MILLIS)
    }

    // Consecutive identical (non-exported) frames seen so far. Drives the idle
    // back-off once it passes [IDLE_BACKOFF_THRESHOLD]. Mutated from the capture
    // loop and reset from the interaction collector, hence atomic.
    private val idleFrameStreak = AtomicInteger(0)

    // While now < this deadline the cadence is pinned to the base (fast) rate so
    // user-driven changes are captured promptly. Written from the interaction
    // collector, read from the capture loop.
    @Volatile
    private var interactionDeadlineMillis: Long = 0L

    /**
     * Requests a [ExportFrame] be taken now.
     */
    suspend fun captureNow() {
        val rawFrame = imageCaptureService.captureRawFrame() ?: return

        val session = sessionManager.getSessionId()
        // A null export means the frame was identical to the previous one
        // (content-based dedup). Feed that back into the cadence so a static
        // screen backs off, while any change snaps us back to the base rate.
        val exportFrame = exportDiffManager.createCaptureEvent(rawFrame, session)
        recordCaptureResult(changed = exportFrame != null)
        if (exportFrame == null) return
        _captureEventFlow.emit(exportFrame)
    }

    /**
     * Current minimum delay between captures, adapted to recent activity.
     *
     * - Inside the post-interaction window the base (fast) rate is used.
     * - Otherwise the delay grows geometrically once the idle streak exceeds
     *   [IDLE_BACKOFF_THRESHOLD], capped at [maxIdleCaptureDelayMillis].
     */
    fun effectiveCaptureDelayMillis(): Long {
        if (captureDelayMillis == Long.MAX_VALUE) return Long.MAX_VALUE
        if (nowMillis() < interactionDeadlineMillis) return captureDelayMillis
        val streak = idleFrameStreak.get()
        if (streak <= IDLE_BACKOFF_THRESHOLD) return captureDelayMillis
        val steps = streak - IDLE_BACKOFF_THRESHOLD
        val scaled = captureDelayMillis * Math.pow(IDLE_BACKOFF_MULTIPLIER, steps.toDouble())
        return scaled.toLong().coerceAtMost(maxIdleCaptureDelayMillis)
    }

    /**
     * Opens a fast-cadence window and resets the idle back-off so user-driven
     * changes are captured promptly.
     */
    fun noteInteraction() {
        interactionDeadlineMillis = nowMillis() + INTERACTION_SPEEDUP_WINDOW_MILLIS
        idleFrameStreak.set(0)
    }

    /**
     * Records whether the most recent capture produced a change, driving the
     * idle back-off. A change resets the streak (speed back up); an identical
     * frame extends it (slow down).
     */
    private fun recordCaptureResult(changed: Boolean) {
        if (changed) idleFrameStreak.set(0) else idleFrameStreak.incrementAndGet()
    }

    private fun nowMillis(): Long = System.nanoTime() / 1_000_000L

    private companion object {
        const val IDLE_BACKOFF_THRESHOLD = 3
        const val IDLE_BACKOFF_MULTIPLIER = 2.0
        const val INTERACTION_SPEEDUP_WINDOW_MILLIS = 1000L
        const val MAX_IDLE_CEILING_MILLIS = 2000L
    }
}
