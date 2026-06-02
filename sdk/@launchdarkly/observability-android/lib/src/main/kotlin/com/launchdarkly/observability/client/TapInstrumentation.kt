package com.launchdarkly.observability.client

import android.app.Activity
import android.app.Application
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.KeyboardShortcutGroup
import android.view.Menu
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.Window
import androidx.annotation.RequiresApi
import kotlin.math.abs

/**
 * Emits a `click` span for each tap on the most recently resumed window.
 *
 * Hooks window touches by decorating each [Window.Callback] (the same approach used by the
 * session-replay `InteractionSource`), but produces OpenTelemetry spans rather than replay events.
 * A tap is an ACTION_DOWN followed by an ACTION_UP on the same primary pointer within the
 * platform tap timeout and touch slop.
 *
 * @param emitTap Receives a completed tap: screen name, up-position, and the down/up timestamps
 *   (epoch nanoseconds) so the span can cover the gesture.
 */
internal class TapInstrumentation(
    private val emitTap: (screenName: String, x: Float, y: Float, startEpochNanos: Long, endEpochNanos: Long) -> Unit
) : Application.ActivityLifecycleCallbacks {

    private var mostRecentWindow: Window? = null
    private var currentScreenName: String = "unknown"
    private val interceptedWindows = mutableListOf<Window>()

    private var downX = 0f
    private var downY = 0f
    private var downEventTime = 0L
    private var touchSlop = DEFAULT_TOUCH_SLOP

    private class TouchInterceptor(
        val window: Window,
        val originalCallback: Window.Callback,
        val onTouch: (Window, MotionEvent) -> Unit
    ) : Window.Callback by originalCallback {
        override fun dispatchTouchEvent(event: MotionEvent): Boolean {
            onTouch(window, event)
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

    fun attachToApplication(application: Application) {
        touchSlop = ViewConfiguration.get(application).scaledTouchSlop
        application.registerActivityLifecycleCallbacks(this)
    }

    fun detachFromApplication(application: Application) {
        application.unregisterActivityLifecycleCallbacks(this)
    }

    private fun handleTouch(window: Window, event: MotionEvent) {
        if (mostRecentWindow != window || event.pointerCount < 1) return

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                downEventTime = event.downTime
            }
            MotionEvent.ACTION_UP -> {
                val movedTooFar = abs(event.x - downX) > touchSlop || abs(event.y - downY) > touchSlop
                val duration = event.eventTime - downEventTime
                if (!movedTooFar && duration <= TAP_TIMEOUT_MS) {
                    val reference = System.currentTimeMillis() - SystemClock.uptimeMillis()
                    val startEpochNanos = (reference + downEventTime) * NANOS_PER_MS
                    val endEpochNanos = (reference + event.eventTime) * NANOS_PER_MS
                    emitTap(currentScreenName, event.x, event.y, startEpochNanos, endEpochNanos)
                }
            }
        }
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}

    override fun onActivityStarted(activity: Activity) {
        activity.window?.let { window ->
            if (!interceptedWindows.contains(window)) {
                window.callback = TouchInterceptor(window, window.callback, this::handleTouch)
                interceptedWindows.add(window)
            }
        }
    }

    override fun onActivityResumed(activity: Activity) {
        activity.window?.let { mostRecentWindow = it }
        currentScreenName = activity.javaClass.simpleName
    }

    override fun onActivityPaused(activity: Activity) {}
    override fun onActivityStopped(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
    override fun onActivityDestroyed(activity: Activity) {}

    companion object {
        const val TAP_SPAN_NAME = "click"
        private const val NANOS_PER_MS = 1_000_000L
        private val TAP_TIMEOUT_MS = ViewConfiguration.getLongPressTimeout().toLong()
        private const val DEFAULT_TOUCH_SLOP = 8
    }
}
