package com.launchdarkly.observability.client.screen

import android.app.Activity
import android.app.Application

/**
 * Orchestrates automatic screen-view capture.
 *
 * Registers an [ActivityScreenSource] on the [application] and forwards captured [ScreenView]s to
 * [onScreenView] (typically [com.launchdarkly.observability.client.ObservabilityService.emitScreenView]),
 * the single funnel shared with the manual `trackScreenView` API.
 */
class ScreenViewManager(
    private val application: Application,
    onScreenView: (ScreenView) -> Unit,
) {
    private val source = ActivityScreenSource(onScreenView)
    private var started = false

    fun start() {
        if (started) return
        application.registerActivityLifecycleCallbacks(source)
        started = true
    }

    fun stop() {
        if (!started) return
        application.unregisterActivityLifecycleCallbacks(source)
        started = false
    }

    /**
     * Captures the already-visible screen for [activity], as if `onActivityResumed` had fired.
     * Call this when the SDK initializes after the activity is already running (e.g. React Native),
     * so the first `screen_view` span and `Navigate` event aren't missed. No-op when automatic
     * capture is not running (screens instrumentation disabled).
     */
    fun registerActivity(activity: Activity) {
        if (!started) return
        source.captureCurrent(activity)
    }
}
