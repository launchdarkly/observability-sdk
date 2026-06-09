package com.launchdarkly.observability.client

import android.app.Activity
import android.app.Application
import android.content.res.Resources
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.text.method.PasswordTransformationMethod
import android.view.KeyboardShortcutGroup
import android.view.Menu
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.EditText
import android.widget.TextView
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
                val downX = motionEvent.getX(pointerIndex)
                val downY = motionEvent.getY(pointerIndex)
                // Resolve the tapped view so consumers can describe the click target (web/iOS parity).
                val target = resolveTargetInfo(window, downX, downY)
                _touchFlow.tryEmit(
                    TouchSample(
                        action = MotionEvent.ACTION_DOWN,
                        x = downX,
                        y = downY,
                        timestamp = eventTimeReference + motionEvent.eventTime,
                        targetClassName = target?.className,
                        targetText = target?.text,
                        targetResourceId = target?.resourceId,
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

    /** Resolved description of the view under a touch point. */
    private data class TargetInfo(
        val className: String?,
        val text: String?,
        val resourceId: String?,
    )

    /**
     * Hit-tests the window's view hierarchy to describe the view under ([x], [y]). Coordinates are
     * relative to the window's decor view (as delivered to the [Window.Callback]). Best-effort: any
     * failure resolves to null so touch dispatch is never affected.
     */
    private fun resolveTargetInfo(window: Window, x: Float, y: Float): TargetInfo? {
        return try {
            val target = findDeepestViewAt(window.decorView, x, y) ?: return null
            TargetInfo(
                className = target.javaClass.name,
                text = extractText(target),
                resourceId = resolveResourceId(target),
            )
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Returns the deepest visible view containing the point, translating coordinates into each
     * child's coordinate space (accounting for child offset and parent scroll). Children are walked
     * in reverse draw order so the topmost view wins.
     */
    private fun findDeepestViewAt(root: View, x: Float, y: Float): View? {
        if (root.visibility != View.VISIBLE) return null
        if (root is ViewGroup) {
            for (i in root.childCount - 1 downTo 0) {
                val child = root.getChildAt(i)
                val childX = x - child.left + root.scrollX
                val childY = y - child.top + root.scrollY
                if (childX >= 0 && childX < child.width && childY >= 0 && childY < child.height) {
                    findDeepestViewAt(child, childX, childY)?.let { return it }
                }
            }
        }
        return root
    }

    /**
     * Best-effort, privacy-safe label for the target view. User-entered values are never captured:
     * editable fields contribute only their hint (placeholder), and any password-masked view is
     * skipped entirely. This mirrors the web SDK, which records an element's textContent (labels),
     * never an input's typed value. Non-input [TextView]s (labels, buttons) contribute their text.
     */
    private fun extractText(view: View): String? {
        val raw = when {
            view is EditText -> if (isSensitive(view)) null else view.hint?.toString()
            view is TextView -> if (isSensitive(view)) null else view.text?.toString()
            else -> view.contentDescription?.toString()
        }
        return raw?.takeIf { it.isNotEmpty() }?.take(MAX_TEXT_LENGTH)
    }

    /** True when the field masks its content (password input), so its text must not be captured. */
    private fun isSensitive(view: TextView): Boolean =
        view.transformationMethod is PasswordTransformationMethod

    private fun resolveResourceId(view: View): String? {
        if (view.id == View.NO_ID) return null
        return try {
            view.resources.getResourceEntryName(view.id)
        } catch (_: Resources.NotFoundException) {
            null
        }
    }

    companion object {
        const val CLICK_SPAN_NAME = "click"

        // Cap captured text to match the web SDK's `Click` payload truncation.
        private const val MAX_TEXT_LENGTH = 2000
    }
}
