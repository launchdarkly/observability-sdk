package com.launchdarkly.observability.client.screen

import android.app.Activity
import android.app.Application
import android.os.Bundle

/**
 * Captures screen appearances from Android Activity lifecycle callbacks.
 *
 * Each time an activity is resumed, a [ScreenView] is derived from it and forwarded to
 * [onScreenView]. Activities can customize their reported name/category by implementing
 * [LDScreenNameProvider]; otherwise the name is derived by cleaning the class name (dropping a
 * trailing `Activity`).
 *
 * This is the Android analog of the iOS `UIViewController` swizzle. Screens that are not backed by
 * a distinct activity (e.g. Fragments or Compose destinations) should be reported manually via
 * [com.launchdarkly.observability.sdk.LDObserve.trackScreenView].
 */
internal class ActivityScreenSource(
    private val onScreenView: (ScreenView) -> Unit,
) : Application.ActivityLifecycleCallbacks {

    override fun onActivityResumed(activity: Activity) {
        captureCurrent(activity)
    }

    /**
     * Emits a [ScreenView] for [activity] as if it had just resumed. Used to capture the
     * already-visible screen when the SDK initializes after the activity is already resumed
     * (e.g. the React Native [com.launchdarkly.observability.sdk.LDReplay.registerActivity] path),
     * where no further `onActivityResumed` fires for the current screen.
     */
    fun captureCurrent(activity: Activity) {
        screenView(activity)?.let(onScreenView)
    }

    private fun screenView(activity: Activity): ScreenView? {
        val provider = activity as? LDScreenNameProvider
        val name = provider?.ldScreenName?.takeIf { it.isNotBlank() }
            ?: cleanedName(activity::class.java.simpleName)
        if (name.isBlank()) return null

        return ScreenView(
            name = name,
            screenClass = activity::class.java.simpleName,
            screenId = activity::class.java.name,
            category = provider?.ldScreenCategory,
        )
    }

    /** Drops a trailing `Activity` suffix so `ProfileActivity` becomes `Profile`. */
    private fun cleanedName(className: String): String {
        val trimmed = className.removeSuffix("Activity")
        return trimmed.ifBlank { className }
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
    override fun onActivityStarted(activity: Activity) {}
    override fun onActivityPaused(activity: Activity) {}
    override fun onActivityStopped(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
    override fun onActivityDestroyed(activity: Activity) {}
}
