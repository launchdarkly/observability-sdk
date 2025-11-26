package com.launchdarkly.observability.replay.capture

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Rect
import android.view.View
import android.view.Window
import android.view.WindowManager
import com.launchdarkly.logging.LDLogger
import kotlin.jvm.javaClass

class WindowInspector(private val logger: LDLogger) {

    fun appWindows(appContext: Context? = null): List<WindowEntry> {
        val appUid = appContext?.applicationInfo?.uid
        val views = getRootViews() ?: return emptyList()
        return views.mapNotNull { view ->
            if (appUid != null && view.context.applicationInfo?.uid != appUid) return@mapNotNull null
            if (!view.isAttachedToWindow || !view.isShown) return@mapNotNull null
            if (view.width == 0 || view.height == 0) return@mapNotNull null
            val visibleRect = Rect()
            if (!view.getGlobalVisibleRect(visibleRect)) return@mapNotNull null
            if (visibleRect.width() == 0 || visibleRect.height() == 0) return@mapNotNull null

            val loc = IntArray(2)
            view.getLocationOnScreen(loc)

            val layoutParams = view.layoutParams as? WindowManager.LayoutParams
            val wmType = layoutParams?.type ?: 0


            WindowEntry(
                rootView = view,
                type = determineWindowType(wmType),
                wmType = wmType,
                width = view.width,
                height = view.height,
                screenLeft = loc[0],
                screenTop = loc[1]
            )
        }
    }

    /**
     * Attempts to discover the owning Dialog or Activity for the given root view.
     * Returns Pair<Dialog?, Activity?>; either (or both) values may be null.
     */
    fun findOwners(rootView: View): Pair<Dialog?, Activity?> {
        var dialog: Dialog? = null
        var activity: Activity? = null

        // Try reflection to get Window callback (Activity or Dialog)
        try {
            val window: Any? = runCatching {
                val field = rootView.javaClass.getDeclaredField("mWindow")
                field.isAccessible = true
                field.get(rootView)
            }.getOrNull()

            val callback: Any? = runCatching {
                (window as? Window)?.callback ?: window?.javaClass?.getMethod("getCallback")?.invoke(window)
            }.getOrNull()

            when (callback) {
                is Activity -> activity = callback
                is Dialog -> dialog = callback
            }
        } catch (t: Throwable) {
            logger.debug("findOwners reflection failed: ${t.message}")
        }

        // Fallback: unwrap the Context to find an Activity
        if (activity == null) {
            try {
                var ctx: Context? = rootView.context
                while (ctx is ContextWrapper) {
                    if (ctx is Activity) {
                        activity = ctx
                        break
                    }
                    ctx = ctx.baseContext
                }
            } catch (t: Throwable) {
                logger.debug("findOwners context unwrap failed: ${t.message}")
            }
        }

        return Pair(dialog, activity)
    }

    /**
     * Attempts to retrieve the [Window] instance backing the provided [rootView].
     *
     * Strategy:
     * 1) Reflection to access DecorView.mWindow (covers Activity and Dialog windows)
     * 2) Reflection to call getWindow() if present
     * 3) Context unwrap to Activity and return activity.window (best-effort fallback)
     */
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
            var ctx: Context? = rootView.context
            while (ctx is ContextWrapper) {
                if (ctx is Activity) {
                    return ctx.window
                }
                ctx = ctx.baseContext
            }
        } catch (t: Throwable) {
            logger.debug("findWindow via context unwrap failed: ${t.message}")
        }

        logger.debug("findWindow: could not determine Window for ${rootView.javaClass.name}")
        return null
    }

    fun getRootViews(): List<View> {
        return try {
            val wmgClass = Class.forName("android.view.WindowManagerGlobal")
            val instance = wmgClass.getMethod("getInstance").invoke(null)

            // 1) Try hidden but "public" API: getRootViews()
            val getRootViewsMethod = runCatching {
                wmgClass.getDeclaredMethod("getRootViews").apply { isAccessible = true }
            }.getOrNull()

            if (getRootViewsMethod != null) {
                val result = getRootViewsMethod.invoke(instance)
                return when (result) {
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
                val result = mViewsField.get(instance)
                return when (result) {
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

            WindowManager.LayoutParams.TYPE_APPLICATION_PANEL,
            WindowManager.LayoutParams.TYPE_APPLICATION_SUB_PANEL,
            WindowManager.LayoutParams.TYPE_TOAST -> WindowType.POPUP

            else -> WindowType.OTHER
        }
    }
}