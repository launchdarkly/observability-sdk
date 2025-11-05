package com.launchdarkly.observability.replay

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.view.MotionEvent
import android.view.Window
import io.opentelemetry.android.session.SessionManager
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class InteractionSource(
    private val sessionManager: SessionManager,
) : Application.ActivityLifecycleCallbacks {

    private var _mostRecentWindow: Window? = null
    private var _interceptedWindows: MutableList<Window> = mutableListOf()

    // Configure with buffer capacity to prevent blocking on emission
    // Using tryEmit() instead of emit() to avoid blocking the UI thread
    private val _captureEventFlow = MutableSharedFlow<InteractionEvent>(
        extraBufferCapacity = 64, // Buffer up to 64 events before dropping
        onBufferOverflow = BufferOverflow.DROP_LATEST
    )
    val captureFlow: SharedFlow<InteractionEvent> = _captureEventFlow.asSharedFlow()

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

    private fun handleInteraction(window: Window, motionEvent: MotionEvent) {
        when (motionEvent.action) {
            MotionEvent.ACTION_DOWN -> {
                // only handle touches on the most recent activity
                if (_mostRecentWindow == window) {
                    val interaction = InteractionEvent(
                        x = motionEvent.x.toInt(), // TODO: look more into if float is preferred here
                        y = motionEvent.y.toInt(), // TODO: look more into if float is preferred here
                        maxX = window.decorView.width,
                        maxY = window.decorView.height,
                        timestamp = System.currentTimeMillis(), // TODO: figure out if there is a way to get absolute wall time from motion event's uptime value
                        session = sessionManager.getSessionId(),
                    )
                    // Use tryEmit() with buffering instead of emit() and coroutine dispatch for speed
                    _captureEventFlow.tryEmit(interaction) // TODO: tryEmit is more performant than dispatching to coroutine/suspend
                }
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
