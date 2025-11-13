package com.launchdarkly.observability.replay

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.os.SystemClock
import android.view.MotionEvent
import android.view.Window
import io.opentelemetry.android.session.SessionManager
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * This class will report [InteractionEvent]s for the primary pointer of the most recently
 * resumed window.
 */
class InteractionSource(
    private val sessionManager: SessionManager,
) : Application.ActivityLifecycleCallbacks {

    // Configure with buffer capacity to prevent blocking on emission
    // Using tryEmit() instead of emit() to avoid blocking the UI thread
    private val _captureEventFlow = MutableSharedFlow<InteractionEvent>(
        extraBufferCapacity = 64, // Buffer up to 64 events before dropping
        onBufferOverflow = BufferOverflow.DROP_LATEST
    )

    // Interactions from the most recent window will be reported periodically on this flow.
    val captureFlow: SharedFlow<InteractionEvent> = _captureEventFlow.asSharedFlow()

    private var _mostRecentWindow: Window? = null
    private var _interceptedWindows: MutableList<Window> = mutableListOf()
    private var _watchedPointerId: Int = -1
    private var _moveGrouper: InteractionMoveGrouper = InteractionMoveGrouper(sessionManager, _captureEventFlow)

    // Instances of this private class will be attached to windows as they are started and this
    // gives this interaction source a hook into window touches.
    private class InteractionDetector(
        val window: Window,
        val originalCallback: Window.Callback,
        val onInteraction: (Window, MotionEvent) -> Unit
    ) : Window.Callback by originalCallback {
        override fun dispatchTouchEvent(event: MotionEvent): Boolean {
            onInteraction(window, event)
            return originalCallback.dispatchTouchEvent(event)
        }
    }

    // This method will be invoked by any / all interceptors that receive interactions.  This is
    // assumed to only be invoked from one thread, the main UI thread and has no multi-threading
    // protections.
    private fun handleInteraction(window: Window, motionEvent: MotionEvent) {
        // only handle touches on the most recent window and only motion events with pointers
        if (_mostRecentWindow != window || motionEvent.pointerCount < 1) {
            return
        }

        _watchedPointerId = _watchedPointerId
            .takeIf { motionEvent.findPointerIndex(it) != -1 } //continue using watched pointer if it exists
            ?: motionEvent.getPointerId(0) // otherwise use first pointer
        val pointerIndex = motionEvent.findPointerIndex(_watchedPointerId)
        val eventTimeReference = System.currentTimeMillis() - SystemClock.uptimeMillis()

        when (motionEvent.action) {
            MotionEvent.ACTION_DOWN -> {
                val interaction = InteractionEvent(
                    action = motionEvent.action,
                    positions = listOf(
                        Position(
                            x = motionEvent.getX(pointerIndex).toInt(),
                            y = motionEvent.getY(pointerIndex).toInt(),
                            timestamp = eventTimeReference + motionEvent.eventTime,
                        ),
                    ),
                    session = sessionManager.getSessionId(),
                )
                // Use tryEmit() with buffering instead of emit() and coroutine dispatch for speed
                _captureEventFlow.tryEmit(interaction) // tryEmit with buffering is more performant than dispatching to coroutine/suspend
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {

                val x = motionEvent.getX(pointerIndex).toInt()
                val y = motionEvent.getY(pointerIndex).toInt()
                val timestamp = eventTimeReference + motionEvent.eventTime

                _moveGrouper.completeWithLastPosition(x, y, timestamp)

                val interaction = InteractionEvent(
                    action = MotionEvent.ACTION_UP, // for the purposes of replay, we can treat CANCEL as UP
                    positions = listOf(
                        Position(
                            x = x,
                            y = y,
                            timestamp = timestamp,
                        ),
                    ),
                    session = sessionManager.getSessionId(),
                )
                // Use tryEmit() with buffering instead of emit() for performance
                _captureEventFlow.tryEmit(interaction)
            }
            MotionEvent.ACTION_MOVE -> {
                // the move grouper provides rate limiting and grouping of positions by time and distance,
                // ultimately to reduce bandwidth consumption.  the move grouper is responsible for
                // calling tryEmit()

                // handle non-current positions
                for (h in 0 until motionEvent.historySize) {
                    _moveGrouper.handleMove(
                        x = motionEvent.getHistoricalX(pointerIndex, h).toInt(),
                        y = motionEvent.getHistoricalY(pointerIndex, h).toInt(),
                        timestamp = eventTimeReference + motionEvent.getHistoricalEventTime(h)
                    )
                }

                // handle current position
                _moveGrouper.handleMove(
                    x = motionEvent.getX(pointerIndex).toInt(),
                    y = motionEvent.getY(pointerIndex).toInt(),
                    timestamp = eventTimeReference + motionEvent.eventTime
                )
            }
        }
    }

    /**
     * Attaches the [InteractionSource] to the [Application] whose [Activity]s will be captured.
     */
    fun attachToApplication(application: Application) {
        application.registerActivityLifecycleCallbacks(this)
    }

    /**
     * Detaches the [InteractionSource] from the [Application].
     */
    fun detachFromApplication(application: Application) {
        application.unregisterActivityLifecycleCallbacks(this)
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        // Noop
    }

    override fun onActivityStarted(activity: Activity) {
        // here we add an interception decorator if the window has never been seen before
        activity.window?.let { window ->
            // if window is not already intercepted
            if (!_interceptedWindows.contains(window)) {
                // create interceptor
                val interceptor = InteractionDetector(
                    window,
                    window.callback,
                    this@InteractionSource::handleInteraction
                )
                // apply interceptor to window
                window.callback = interceptor
                // track that this activity is intercepted so we don't intercept again in the future
                _interceptedWindows.add(window)
            }
        }
    }

    override fun onActivityResumed(activity: Activity) {
        // here we update to follow the most recent window
        activity.window?.let { window ->
            _mostRecentWindow = window
        }
    }

    override fun onActivityPaused(activity: Activity) {
        // Noop
    }

    override fun onActivityStopped(activity: Activity) {
        // Noop
    }

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {
        // Noop
    }

    override fun onActivityDestroyed(activity: Activity) {
        // Noop
    }
}
