package com.launchdarkly.observability.replay.capture

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Rect
import android.os.Build
import android.view.View
import android.view.Window
import android.view.WindowManager
import com.launchdarkly.observability.context.ObserveLogger
import com.launchdarkly.observability.replay.utils.locationOnScreen
import kotlin.jvm.javaClass

class WindowInspector(private val logger: ObserveLogger) {

    fun appWindows(appContext: Context? = null): List<WindowEntry> {
        val appUid = appContext?.applicationInfo?.uid
        return rootViews().mapNotNull { view ->
            if (appUid != null && view.context.applicationInfo?.uid != appUid) return@mapNotNull null
            if (!view.isAttachedToWindow || !view.isShown) return@mapNotNull null
            if (view.width == 0 || view.height == 0) return@mapNotNull null

            val visibleRect = Rect()
            if (!view.getGlobalVisibleRect(visibleRect)) return@mapNotNull null
            if (visibleRect.width() == 0 || visibleRect.height() == 0) return@mapNotNull null

            val (screenX, screenY) = view.locationOnScreen()

            val layoutParams = view.layoutParams as? WindowManager.LayoutParams
            val wmType = layoutParams?.type ?: 0

            WindowEntry(
                rootView = view,
                type = determineWindowType(wmType),
                layoutParams = layoutParams,
                width = view.width,
                height = view.height,
                screenLeft = screenX.toInt(),
                screenTop = screenY.toInt()
            )
        }
    }

    /**
     * The topmost activity window this process currently has on screen, paired with the activity
     * hosting it when that can be resolved, or null when there is no activity window yet.
     *
     * Activity lifecycle callbacks are never replayed, so an instrumentation that installs after an
     * activity has already resumed learns nothing about it from
     * [android.app.Application.ActivityLifecycleCallbacks] - and in a single-activity app, nothing
     * ever will until the app is backgrounded and brought back. Asking the platform which windows
     * exist is the way to recover in that case.
     *
     * Deliberately does not reuse [appWindows]: its filters describe windows that are ready to be
     * *rendered*, while an activity is added to the window manager during `onResume` and only
     * measured on a later traversal. During startup - exactly when this recovery is needed - the
     * window may therefore still be zero-sized and would be filtered out.
     *
     * Main thread only: it reads live view state.
     */
    fun topmostAppWindow(appContext: Context? = null): OnScreenWindow? {
        val appUid = appContext?.applicationInfo?.uid
        // Views come back in the order their windows were added, so the last one is the topmost.
        return rootViews().asReversed().firstNotNullOfOrNull { view ->
            if (appUid != null && view.context.applicationInfo?.uid != appUid) {
                return@firstNotNullOfOrNull null
            }
            val layoutParams = view.layoutParams as? WindowManager.LayoutParams
            if (determineWindowType(layoutParams?.type ?: 0) != WindowType.ACTIVITY) {
                return@firstNotNullOfOrNull null
            }
            findWindow(view)?.let { OnScreenWindow(it, activityOf(it)) }
        }
    }

    /** The root view of every window this process owns, newest last. */
    private fun rootViews(): List<View> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            android.view.inspector.WindowInspector.getGlobalWindowViews().map { it.rootView }
        } else {
            getRootViews()
        }

    /**
     * The [Activity] hosting [window], when it can be resolved.
     *
     * An activity is its own window's callback ([Activity] implements [Window.Callback]) unless
     * something has wrapped it - this SDK's touch interceptor does, and so does AppCompat - in which
     * case the window's context still leads there. Note that a *decor view's* context never does: it
     * is a `DecorContext` over the application context, precisely so the decor cannot retain the
     * activity, which is why this resolves from the window rather than from the view.
     */
    private fun activityOf(window: Window): Activity? =
        window.callback as? Activity ?: activityOf(window.context)

    /** Walks a context's wrapper chain looking for the [Activity] behind it. */
    private fun activityOf(context: Context?): Activity? {
        var current: Context? = context
        while (current is ContextWrapper) {
            if (current is Activity) return current
            current = current.baseContext
        }
        return null
    }

    /**
     * Attempts to retrieve the [Window] instance backing the provided [rootView].
     *
     * Strategy:
     * 1) Reflection to access DecorView.mWindow (covers Activity and Dialog windows)
     * 2) Reflection to call getWindow() if present
     * 3) Context unwrap to Activity and return activity.window (best-effort fallback)
     */
    @SuppressLint("PrivateApi")
    fun findWindow(rootView: View): Window? {
        // 1) Try to read a private field "mWindow" (present on DecorView/PopupDecorView)
        try {
            var currentClass: Class<*>? = rootView.javaClass
            while (currentClass != null) {
                val field = runCatching { currentClass.getDeclaredField("mWindow") }.getOrNull()
                if (field != null) {
                    field.isAccessible = true
                    val candidate = field.get(rootView)
                    (candidate as? Window)?.let { return it }
                }
                currentClass = currentClass.superclass
            }
        } catch (t: Throwable) {
            logger.debug("findWindow via mWindow failed: ${t.message}")
        }

        // 2) Try to invoke a getWindow() method if it exists
        try {
            var currentClass: Class<*>? = rootView.javaClass
            while (currentClass != null) {
                val method = runCatching { currentClass.getDeclaredMethod("getWindow") }.getOrNull()
                if (method != null) {
                    method.isAccessible = true
                    val candidate = runCatching { method.invoke(rootView) }.getOrNull()
                    (candidate as? Window)?.let { return it }
                }
                currentClass = currentClass.superclass
            }
        } catch (t: Throwable) {
            logger.debug("findWindow via getWindow() failed: ${t.message}")
        }

        // 3) Fallback: unwrap context to Activity and return Activity.window
        try {
            activityOf(rootView.context)?.let { return it.window }
        } catch (t: Throwable) {
            logger.debug("findWindow via context unwrap failed: ${t.message}")
        }

        logger.debug("findWindow: could not determine Window for ${rootView.javaClass.name}")
        return null
    }

    @SuppressLint("PrivateApi")
    fun getRootViews(): List<View> {
        return try {
            val wmgClass = Class.forName("android.view.WindowManagerGlobal")
            val instance = wmgClass.getMethod("getInstance").invoke(null)

            // 1) Try hidden but "public" API: getRootViews()
            val getRootViewsMethod = runCatching {
                wmgClass.getDeclaredMethod("getRootViews").apply { isAccessible = true }
            }.getOrNull()

            if (getRootViewsMethod != null) {
                return when (val result = getRootViewsMethod.invoke(instance)) {
                    is Array<*> -> result.filterIsInstance<View>()
                    is List<*> -> result.filterIsInstance<View>()
                    else -> emptyList()
                }
            }

            // 2) Fallback to private field: mViews
            val mViewsField = runCatching {
                wmgClass.getDeclaredField("mViews").apply { isAccessible = true }
            }.getOrNull()

            if (mViewsField != null) {
                return when (val result = mViewsField.get(instance)) {
                    is Array<*> -> result.filterIsInstance<View>()
                    is List<*> -> result.filterIsInstance<View>()
                    else -> emptyList()
                }
            }

            emptyList()
        } catch (e: Throwable) {
            logger.error("Failed to get root views", e)
            emptyList()
        }
    }

    private fun determineWindowType(wmType: Int): WindowType {
        return when (wmType) {
            WindowManager.LayoutParams.TYPE_APPLICATION,
            WindowManager.LayoutParams.TYPE_BASE_APPLICATION,
            WindowManager.LayoutParams.TYPE_APPLICATION_STARTING -> WindowType.ACTIVITY
            WindowManager.LayoutParams.TYPE_APPLICATION_ATTACHED_DIALOG -> WindowType.DIALOG
            else -> WindowType.OTHER
        }
    }
}