package com.launchdarkly.observability.client

import android.app.Activity
import android.app.Application
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.KeyboardShortcutGroup
import android.view.Menu
import android.view.MotionEvent
import android.view.Window
import androidx.annotation.RequiresApi
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * A single hook for intercepting window touches, owned by the Observability plugin.
 *
 * This mirrors the iOS `UserInteractionManager`: it is the one place that captures touches and
 * exposes them as a stream of raw, unscaled [TouchSample]s. Multiple consumers subscribe to
 * [touchFlow] - the Observability tap instrumentation (to emit `click` spans) and Session Replay
 * (which applies its own scaling and grouping).
 *
 * Samples are reported for the primary pointer of the most recently resumed window.
 */
class UserInteractionManager : Application.ActivityLifecycleCallbacks {

    // Buffered so emission never blocks the UI thread; oldest samples are dropped under pressure.
    private val _touchFlow = MutableSharedFlow<TouchSample>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    /** Raw, unscaled touch samples from the most recently resumed window. */
    val touchFlow: SharedFlow<TouchSample> = _touchFlow.asSharedFlow()

    private var mostRecentWindow: Window? = null
    private val interceptedWindows: MutableList<Window> = mutableListOf()
    private var watchedPointerId: Int = -1

    private class InteractionDetector(
        val window: Window,
        val originalCallback: Window.Callback,
        val onInteraction: (Window, MotionEvent) -> Unit
    ) : Window.Callback by originalCallback {
        override fun dispatchTouchEvent(event: MotionEvent): Boolean {
            onInteraction(window, event)
            return originalCallback.dispatchTouchEvent(event)
        }

        override fun onProvideKeyboardShortcuts(
            data: MutableList<KeyboardShortcutGroup>?,
            menu: Menu?,
            deviceId: Int
        ) {
            originalCallback.onProvideKeyboardShortcuts(data, menu, deviceId)
        }

        @RequiresApi(Build.VERSION_CODES.O)
        override fun onPointerCaptureChanged(hasCapture: Boolean) {
            originalCallback.onPointerCaptureChanged(hasCapture)
        }
    }

    // Invoked only from the main UI thread; no multi-threading protection needed.
    private fun handleInteraction(window: Window, motionEvent: MotionEvent) {
        if (mostRecentWindow != window || motionEvent.pointerCount < 1) return

        // Nothing is listening; avoid the per-event work entirely.
        if (_touchFlow.subscriptionCount.value == 0) return

        watchedPointerId = watchedPointerId
            .takeIf { motionEvent.findPointerIndex(it) != -1 } // continue with watched pointer if present
            ?: motionEvent.getPointerId(0) // otherwise track the first pointer
        val pointerIndex = motionEvent.findPointerIndex(watchedPointerId)
        if (pointerIndex < 0) return

        val eventTimeReference = System.currentTimeMillis() - SystemClock.uptimeMillis()

        when (motionEvent.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                _touchFlow.tryEmit(
                    TouchSample(
                        action = MotionEvent.ACTION_DOWN,
                        x = motionEvent.getX(pointerIndex),
                        y = motionEvent.getY(pointerIndex),
                        timestamp = eventTimeReference + motionEvent.eventTime,
                    )
                )
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                // Preserve UP vs CANCEL so consumers can decide: Session Replay treats both as the
                // end of a gesture, while tap detection ignores CANCEL.
                _touchFlow.tryEmit(
                    TouchSample(
                        action = motionEvent.actionMasked,
                        x = motionEvent.getX(pointerIndex),
                        y = motionEvent.getY(pointerIndex),
                        timestamp = eventTimeReference + motionEvent.eventTime,
                    )
                )
            }
            MotionEvent.ACTION_MOVE -> {
                // Emit each historical sample followed by the current one so consumers that group
                // moves (e.g. Session Replay) keep full fidelity.
                for (h in 0 until motionEvent.historySize) {
                    _touchFlow.tryEmit(
                        TouchSample(
                            action = MotionEvent.ACTION_MOVE,
                            x = motionEvent.getHistoricalX(pointerIndex, h),
                            y = motionEvent.getHistoricalY(pointerIndex, h),
                            timestamp = eventTimeReference + motionEvent.getHistoricalEventTime(h),
                        )
                    )
                }
                _touchFlow.tryEmit(
                    TouchSample(
                        action = MotionEvent.ACTION_MOVE,
                        x = motionEvent.getX(pointerIndex),
                        y = motionEvent.getY(pointerIndex),
                        timestamp = eventTimeReference + motionEvent.eventTime,
                    )
                )
            }
        }
    }

    /** Attaches to the [Application] whose activities' touches will be captured. */
    fun attachToApplication(application: Application) {
        application.registerActivityLifecycleCallbacks(this)
    }

    /**
     * Registers an already-running activity for touch capture, as if [onActivityStarted] and
     * [onActivityResumed] had fired. Call this when the SDK initializes after the activity is
     * already running (e.g. React Native).
     */
    fun registerActivity(activity: Activity) {
        onActivityStarted(activity)
        onActivityResumed(activity)
    }

    /** Detaches from the [Application]. */
    fun detachFromApplication(application: Application) {
        application.unregisterActivityLifecycleCallbacks(this)
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}

    override fun onActivityStarted(activity: Activity) {
        activity.window?.let { window ->
            if (!interceptedWindows.contains(window)) {
                window.callback = InteractionDetector(window, window.callback, this::handleInteraction)
                interceptedWindows.add(window)
            }
        }
    }

    override fun onActivityResumed(activity: Activity) {
        activity.window?.let { mostRecentWindow = it }
    }

    override fun onActivityPaused(activity: Activity) {}
    override fun onActivityStopped(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
    override fun onActivityDestroyed(activity: Activity) {}

    companion object {
        const val CLICK_SPAN_NAME = "click"
    }
}
