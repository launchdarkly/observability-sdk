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
import com.launchdarkly.observability.R
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

    /**
     * Supplies the active screen (`event.screen_id`, `event.screen_name`) at touch-capture time.
     * Invoked on the main UI thread inside [handleInteraction] - before the touch is dispatched to
     * app handlers that may navigate synchronously - so the captured screen is the one the user
     * tapped, not a destination screen. Defaults to no screen until a consumer wires it up.
     */
    var screenInfoProvider: () -> Pair<String?, String?> = { null to null }

    private var mostRecentWindow: Window? = null
    private val interceptedWindows: MutableList<Window> = mutableListOf()
    private var watchedPointerId: Int = -1
    private var attachedApplication: Application? = null
    // Window-callback wrapping (the invasive part) is deferred until a consumer enables it; until
    // then the manager only tracks the current window so a late enable can wrap it.
    private var captureEnabled = false

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
                // Read the active screen now, on the main thread and before the original
                // dispatchTouchEvent runs, so a synchronous navigation in the tap handler can't make
                // the click report the destination screen instead of the tapped one.
                val (screenId, screenName) = screenInfoProvider()
                _touchFlow.tryEmit(
                    TouchSample(
                        action = MotionEvent.ACTION_DOWN,
                        x = downX,
                        y = downY,
                        timestamp = eventTimeReference + motionEvent.eventTime,
                        targetClassName = target?.className,
                        targetText = target?.text,
                        targetResourceId = target?.resourceId,
                        screenId = screenId,
                        screenName = screenName,
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

    /**
     * Attaches to the [Application] so the manager can track the current activity's window.
     * Idempotent. This is the benign half of the hook: it registers lifecycle callbacks but does
     * NOT wrap any window callback or hit-test until [enableTouchCapture] is called. It is invoked
     * unconditionally during Observability init so that, whenever a consumer later enables capture
     * (e.g. Session Replay starts recording after the first activity is already running), the
     * already-current window is known and can be wrapped immediately.
     */
    fun attachToApplication(application: Application) {
        if (attachedApplication != null) return
        attachedApplication = application
        application.registerActivityLifecycleCallbacks(this)
    }

    /**
     * Enables touch capture: wraps the current window's callback right away and every subsequently
     * started window's callback. Idempotent. Called by the Observability tap instrumentation (gated
     * by [ObservabilityOptions.Instrumentations.userTaps]) and by Session Replay, whichever needs
     * it first.
     *
     * Wrapping the current window here (rather than relying solely on future [onActivityStarted]
     * callbacks) is what keeps a late enable - capture starting after the first activity's
     * `onActivityStarted` has already fired - from missing that activity's touches.
     */
    fun enableTouchCapture() {
        captureEnabled = true
        mostRecentWindow?.let { interceptWindow(it) }
    }

    /** Wraps [window]'s callback once so touches flow through [handleInteraction]. Idempotent. */
    private fun interceptWindow(window: Window) {
        if (interceptedWindows.contains(window)) return
        window.callback = InteractionDetector(window, window.callback, this::handleInteraction)
        interceptedWindows.add(window)
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
        attachedApplication = null
        captureEnabled = false
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}

    override fun onActivityStarted(activity: Activity) {
        if (!captureEnabled) return
        activity.window?.let { interceptWindow(it) }
    }

    override fun onActivityResumed(activity: Activity) {
        activity.window?.let { window ->
            mostRecentWindow = window
            // Capture may have been enabled after this activity started; ensure its window is
            // wrapped so the current screen's touches are captured.
            if (captureEnabled) interceptWindow(window)
        }
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
            // Compose renders into a single AndroidComposeView, so the native hit-test bottoms out
            // either at that host or at one of its internal platform children (e.g.
            // AndroidViewsHandler, ViewLayer) that overlay the Compose area. In those cases the real
            // target is a composable, so walk up to the host (composeHostFor) and resolve against
            // the Compose semantics tree. The class-name guard keeps ComposeClickResolver (and its
            // Compose symbols) from loading in apps that don't ship Compose UI. Coordinates here are
            // window-relative, matching SemanticsNode.boundsInWindow.
            composeHostFor(target)?.let { host ->
                // The tap landed on Compose, so `target` is an internal platform view whose
                // class/text/id are meaningless. Prefer composable semantics; if they can't be
                // resolved (e.g. no semantics at the point), describe the Compose host itself rather
                // than falling back to the internal child, which would emit a misleading
                // `event.classname`/`event.tag` and drop `Modifier.ldId`/`event.id`.
                ComposeClickResolver.resolve(host, x, y)?.let { info ->
                    return TargetInfo(
                        className = info.role,
                        text = info.text?.take(MAX_TEXT_LENGTH),
                        resourceId = info.ldId,
                    )
                }
                return TargetInfo(
                    className = host.javaClass.name,
                    text = extractText(host),
                    resourceId = resolveResourceId(host),
                )
            }
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
     * Returns the enclosing `AndroidComposeView` host when [view] is the host itself or one of
     * Compose's internal platform children, or null otherwise.
     *
     * The native hit-test in [findDeepestViewAt] can bottom out at any of Compose's internal
     * full-bleed platform views (e.g. `AndroidViewsHandler`, `ViewLayer`, `DrawChildContainer`)
     * rather than the host, which would otherwise hide the actual composable (and any `ldId`)
     * behind a meaningless internal target. So climb through any view in Compose's internal
     * platform namespace (`androidx.compose.ui.*`) until the host is found. Climbing stops at the
     * first view outside that namespace - a real `AndroidView` interop child or unrelated native
     * view - so genuine native content keeps its own identity. Detected by class name (the types
     * aren't public API) so this never references Compose symbols directly.
     */
    private fun composeHostFor(view: View): View? {
        var current: View? = view
        while (current != null) {
            val name = current::class.java.name
            if (name.contains("AndroidComposeView")) return current
            if (!name.startsWith("androidx.compose.ui.")) return null
            current = current.parent as? View
        }
        return null
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
                // Skip views that don't take part in touch delivery so hit-testing looks past
                // them to the content beneath - exactly as Android's real dispatch does. Without
                // this, React Native's full-screen `DebuggingOverlay` (a non-interactive highlight
                // layer drawn on top of everything in dev builds) captures every tap, so the
                // resolved target is the overlay and the real button's text/id - including
                // `nativeID` - is lost.
                if (isTouchTransparent(child)) continue
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
     * True when [view] does not participate in touch delivery, so hit-testing must look past it to
     * whatever is drawn beneath. Covers React Native's debug-only full-screen `DebuggingOverlay`
     * (a plain, non-interactive `View` that draws element highlights on top of everything) and any
     * React Native view declared with `pointerEvents="none"`. Detected without a compile-time
     * dependency on React Native: the overlay by class name, pointer-events via the
     * `ReactPointerEventsView` interface. Best-effort - any reflection failure resolves to false so
     * hit-testing simply falls back to its previous behaviour.
     */
    private fun isTouchTransparent(view: View): Boolean {
        if (view::class.java.name == DEBUGGING_OVERLAY_CLASS_NAME) return true
        val getter = pointerEventsGetter ?: return false
        if (pointerEventsViewClass?.isInstance(view) != true) return false
        return runCatching {
            // enum PointerEvents { BOX_NONE, NONE, BOX_ONLY, AUTO }; only NONE makes the whole
            // subtree transparent to touches, so it is the only value that lets us skip it here.
            (getter.invoke(view) as? Enum<*>)?.name == "NONE"
        }.getOrDefault(false)
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
        // An explicit `ldId(...)` always wins over inferred identifiers.
        resolveLdId(view)?.let { return it }
        // React Native's `nativeID` is the explicit, developer-supplied analytics id for RN apps
        // (set directly or via <LDClick id=...>). It is distinct from `testID` (which is overloaded
        // for e2e tests and stripped by session-replay privacy masking), so it wins over inferred
        // native ids and the `testID` fallback below.
        resolveReactNativeId(view)?.let { return it }
        if (view.id != View.NO_ID) {
            try {
                return view.resources.getResourceEntryName(view.id)
            } catch (_: Resources.NotFoundException) {
                // Fall through to the React Native testID lookup below.
            }
        }
        // React Native views typically have no native id; RN stores the JS `testID` prop on the
        // `react_test_id` tag (the same identifier the privacy matchers use), so fall back to it.
        return resolveReactTestId(view)
    }

    /**
     * Reads the developer-supplied `ldId(...)` tag, walking up the view hierarchy so a tap that
     * resolves to a child of a tagged view still reports its analytics id. Returns null when no
     * ancestor carries an id.
     */
    internal fun resolveLdId(view: View): String? {
        var current: View? = view
        while (current != null) {
            (current.getTag(R.id.ld_id_tag) as? String)?.takeIf { it.isNotEmpty() }?.let { return it }
            current = current.parent as? View
        }
        return null
    }

    /** Reads React Native's JS `testID` prop from the `react_test_id` tag, when present. */
    private fun resolveReactTestId(view: View): String? {
        val resId = reactTestIdResId ?: return null
        return (view.getTag(resId) as? String)?.takeIf { it.isNotEmpty() }
    }

    /**
     * Reads the React Native `nativeID` prop, walking up the view hierarchy so a tap that lands on a
     * child of a tagged view (e.g. an `<LDClick id=...>` wrapper) still reports its analytics id. RN
     * stores the JS `nativeID` prop on the `view_tag_native_id` tag; resolved reflectively to avoid a
     * compile-time dependency on React Native. Returns null when no ancestor carries a `nativeID` (or
     * React Native isn't on the runtime classpath).
     */
    private fun resolveReactNativeId(view: View): String? {
        val resId = reactNativeIdResId ?: return null
        var current: View? = view
        while (current != null) {
            (current.getTag(resId) as? String)?.takeIf { it.isNotEmpty() }?.let { return it }
            current = current.parent as? View
        }
        return null
    }

    companion object {
        const val CLICK_SPAN_NAME = "click"

        // Cap captured text to match the web SDK's `Click` payload truncation.
        private const val MAX_TEXT_LENGTH = 2000

        // React Native's dev-only highlight layer. It is a plain, non-interactive `View` added
        // full-screen on top of every surface, so a naive geometric hit-test would resolve every
        // tap to it instead of the real target beneath.
        private const val DEBUGGING_OVERLAY_CLASS_NAME =
            "com.facebook.react.views.debuggingoverlay.DebuggingOverlay"

        /**
         * React Native's `ReactPointerEventsView` interface, implemented by views that expose a
         * `pointerEvents` value. Resolved reflectively to avoid a compile-time dependency on React
         * Native; `null` when the RN library isn't on the runtime classpath.
         */
        private val pointerEventsViewClass: Class<*>? by lazy {
            runCatching {
                Class.forName("com.facebook.react.uimanager.ReactPointerEventsView")
            }.getOrNull()
        }

        /** The `getPointerEvents()` accessor on [pointerEventsViewClass], when available. */
        private val pointerEventsGetter: java.lang.reflect.Method? by lazy {
            runCatching { pointerEventsViewClass?.getMethod("getPointerEvents") }.getOrNull()
        }

        /**
         * Resource id of React Native's `react_test_id` tag, where RN stores the JS `testID` prop
         * on each view. Resolved reflectively to avoid a compile-time dependency on React Native;
         * `null` when the RN library isn't on the runtime classpath. Mirrors the lookup used by the
         * Session Replay privacy matchers.
         */
        private val reactTestIdResId: Int? by lazy {
            runCatching {
                Class.forName("com.facebook.react.R\$id")
                    .getField("react_test_id")
                    .getInt(null)
            }.getOrNull()
        }

        /**
         * Resource id of React Native's `view_tag_native_id` tag, where RN stores the JS `nativeID`
         * prop on each view. Resolved reflectively to avoid a compile-time dependency on React Native;
         * `null` when the RN library isn't on the runtime classpath.
         */
        private val reactNativeIdResId: Int? by lazy {
            runCatching {
                Class.forName("com.facebook.react.R\$id")
                    .getField("view_tag_native_id")
                    .getInt(null)
            }.getOrNull()
        }
    }
}
