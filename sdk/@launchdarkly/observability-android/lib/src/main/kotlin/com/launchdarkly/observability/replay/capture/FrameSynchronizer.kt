package com.launchdarkly.observability.replay.capture

import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Choreographer
import android.view.View
import android.view.ViewTreeObserver
import android.view.Window
import com.launchdarkly.observability.context.ObserveLogger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Registers [onFrameRendered] to run once [window] finishes rendering a frame and returns a
 * function that unregisters it, or `null` when frame completion can't be observed on this device.
 *
 * Implementations must deliver the callback on the main thread; [FrameSynchronizer] relies on
 * that ordering to tell a frame that completed before a mask sample from one that completed
 * after it.
 */
internal typealias FrameRenderedObserver = (window: Window, onFrameRendered: () -> Unit) -> (() -> Unit)?

/**
 * Keeps what session replay reads out of the view tree in step with the frames the UI renders.
 *
 * Masks are geometry read from the view tree while the frame itself is pixels read back from the
 * window's surface, and the two are produced at different points of the rendering pipeline. A
 * `Choreographer` frame callback runs in the animation phase — *before* that frame's
 * measure/layout/draw — so geometry read there belongs to the previous traversal, and `PixelCopy`
 * returns whatever buffer the surface currently holds, which is typically the frame before the
 * one being drawn. While the screen is scrolling or animating those two disagree by a frame or
 * more, and masks visibly slip off the content they are meant to cover.
 *
 * [sampleAtRenderedFrame] closes both halves of that gap: it reads geometry from inside the draw
 * pass that produces a frame, and it returns only once that frame has finished rendering. Pixels
 * copied afterwards therefore can't be older than the geometry, which is what makes the
 * before/after mask passes a genuine bracket around the captured frame — the assumption
 * [com.launchdarkly.observability.replay.masking.MaskApplier]'s convex hull is built on.
 *
 * Transforms need no separate compensation: native masks go through `View.transformMatrixToGlobal`
 * and Compose's `SemanticsNode.boundsInWindow` follows `graphicsLayer` transforms, so both already
 * describe the transform state of the frame they are sampled in. What neither can see is what the
 * RenderThread applies on its own — the stretch `RenderEffect` behind overscroll being the notable
 * one — and no amount of frame alignment surfaces that.
 *
 * @param drawTimeoutMillis how long to wait for a draw before reading the tree outside one.
 * @param frameTimeoutMillis how long to wait for a frame to finish rendering before giving up.
 * @param postFrameCallback schedules work at the start of the next UI frame.
 * @param observeFrameRendered see [FrameRenderedObserver].
 */
internal class FrameSynchronizer(
    private val logger: ObserveLogger,
    private val drawTimeoutMillis: Long = DRAW_TIMEOUT_MILLIS,
    private val frameTimeoutMillis: Long = FRAME_TIMEOUT_MILLIS,
    private val postFrameCallback: (onFrame: () -> Unit) -> Unit = { onFrame ->
        Choreographer.getInstance().postFrameCallback { onFrame() }
    },
    private val observeFrameRendered: FrameRenderedObserver = ::observeFrameMetrics,
) {
    /** Holds a sampled value so a `null` result stays distinguishable from "not sampled". */
    private class Sampled<T>(val value: T)

    /** Suspends until the start of the next UI frame. */
    suspend fun awaitVsync() {
        suspendCancellableCoroutine { continuation ->
            postFrameCallback {
                if (continuation.isActive) {
                    continuation.resume(Unit)
                }
            }
        }
    }

    /**
     * Reads [sample] from [rootView]'s tree inside the draw pass of the next frame, then waits for
     * that frame to finish rendering before returning.
     *
     * Callers copy pixels once this returns, so the copy sees the frame the geometry was read
     * from rather than an older one. The wait is skipped when there is nothing to wait for: an
     * idle tree that never draws, an unknown [window], or a device below API 24 where no public
     * API reports frame completion. The sample itself is still taken as close to a draw as the
     * device allows.
     */
    suspend fun <T> sampleAtRenderedFrame(rootView: View, window: Window?, sample: () -> T): T {
        var onFrameRendered: (() -> Unit)? = null
        // Registered before the draw so that the frame being sampled is the frame we then wait
        // for; registering afterwards would routinely miss it and wait out the following one.
        val stopObserving = window?.let { observeFrameRendered(it) { onFrameRendered?.invoke() } }

        try {
            // Nothing drew, so there is no frame in flight to wait for: the surface already
            // holds what the fallback read below sees.
            val sampled = awaitDrawSample(rootView, sample) ?: return sample()
            if (stopObserving == null) return sampled.value

            val rendered = CompletableDeferred<Unit>()
            onFrameRendered = { rendered.complete(Unit) }
            // A timeout here means the frame never reached the surface; the after pass is what
            // keeps the resulting masks safe rather than accurate.
            withTimeoutOrNull(frameTimeoutMillis) { rendered.await() }
            return sampled.value
        } finally {
            stopObserving?.invoke()
        }
    }

    /**
     * Runs [sample] from inside the next draw pass of [rootView]'s tree, so the values it reads
     * belong to the frame about to be rendered instead of to a traversal already superseded.
     *
     * Falls back to reading the tree directly when nothing draws within [drawTimeoutMillis]: no
     * draw means no pending traversal, so the tree is idle and reading it now is equivalent.
     */
    suspend fun <T> sampleAtDraw(rootView: View, sample: () -> T): T {
        val sampled = awaitDrawSample(rootView, sample)
        return if (sampled != null) sampled.value else sample()
    }

    private suspend fun <T> awaitDrawSample(rootView: View, sample: () -> T): Sampled<T>? {
        val observer = rootView.viewTreeObserver
        if (observer == null || !observer.isAlive) return null

        return withTimeoutOrNull(drawTimeoutMillis) {
            suspendCancellableCoroutine { continuation ->
                val listener = object : ViewTreeObserver.OnDrawListener {
                    override fun onDraw() {
                        if (!continuation.isActive) return

                        val sampled = try {
                            Sampled(sample())
                        } catch (t: Throwable) {
                            // A failure must not escape into the draw pass; falling back to
                            // sampling outside it surfaces the failure the usual way.
                            logger.warn("Failed to sample the view tree during draw", t)
                            null
                        }
                        removeAfterDraw(rootView, observer, this)
                        continuation.resume(sampled)
                    }
                }

                continuation.invokeOnCancellation { removeAfterDraw(rootView, observer, listener) }

                try {
                    observer.addOnDrawListener(listener)
                } catch (t: Throwable) {
                    logger.debug("Cannot observe draws of ${rootView.javaClass.name}: ${t.message}")
                    continuation.resume(null)
                }
            }
        }
    }

    /**
     * Unregisters [listener] once the draw pass is over. [ViewTreeObserver] rejects removal from
     * inside `onDraw`, so it is deferred to the next main-thread message.
     */
    private fun removeAfterDraw(
        rootView: View,
        observer: ViewTreeObserver,
        listener: ViewTreeObserver.OnDrawListener
    ) {
        rootView.post {
            try {
                // A dead observer throws on removal, and the tree may have been reattached to a
                // fresh one in the meantime.
                val current = if (observer.isAlive) observer else rootView.viewTreeObserver
                current?.removeOnDrawListener(listener)
            } catch (t: Throwable) {
                logger.debug("Failed to remove draw listener: ${t.message}")
            }
        }
    }

    private companion object {
        /**
         * Roughly two frames at 60 Hz: long enough for a scheduled draw to land, short enough
         * that an idle screen doesn't stall the capture.
         */
        const val DRAW_TIMEOUT_MILLIS = 32L

        /** A frame that hasn't finished rendering in four frames at 60 Hz means an idle window. */
        const val FRAME_TIMEOUT_MILLIS = 64L
    }
}

/**
 * Frame completion callbacks are delivered on the main looper. The listener stays registered only
 * for the couple of frames a capture spans and does nothing but signal, and the main looper is
 * also what gives [FrameSynchronizer] its ordering guarantee against the draw pass — both of
 * which beat owning a background thread that nothing in the SDK would shut down.
 */
private val frameMetricsHandler: Handler by lazy { Handler(Looper.getMainLooper()) }

/**
 * [FrameRenderedObserver] backed by `Window.addOnFrameMetricsAvailableListener`, which reports
 * each frame the window finishes rendering. Returns `null` below API 24, where no public API
 * exposes frame completion.
 */
private fun observeFrameMetrics(window: Window, onFrameRendered: () -> Unit): (() -> Unit)? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return null

    val listener = Window.OnFrameMetricsAvailableListener { _, _, _ -> onFrameRendered() }
    try {
        window.addOnFrameMetricsAvailableListener(listener, frameMetricsHandler)
    } catch (_: Throwable) {
        // Windows without a backing ViewRootImpl reject the listener.
        return null
    }

    return {
        try {
            window.removeOnFrameMetricsAvailableListener(listener)
        } catch (_: Throwable) {
            // Already torn down along with the window.
        }
    }
}
