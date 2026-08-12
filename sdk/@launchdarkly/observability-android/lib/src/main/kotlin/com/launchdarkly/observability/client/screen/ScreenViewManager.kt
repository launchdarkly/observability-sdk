package com.launchdarkly.observability.client.screen

import android.app.Activity
import android.app.Application
import com.launchdarkly.observability.client.ScreenViewCapturing

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
) : ScreenViewCapturing {
    private val source = ActivityScreenSource(onScreenView)
    private var started = false

    override fun start() {
        if (started) return
        application.registerActivityLifecycleCallbacks(source)
        started = true
    }

    override fun stop() {
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
    override fun registerActivity(activity: Activity) {
        if (!started) return
        source.captureCurrent(activity)
    }

    /**
     * Re-emits the screen the user is currently viewing, as if `onActivityResumed` had fired. Used
     * to seed a fresh session (after a session-id change) so the new session gets an opening
     * `screen_view` span and `Navigate` event even though no `onActivityResumed` fires for the
     * already-resumed activity. No-op when automatic capture is not running.
     */
    override fun captureCurrentScreen() {
        if (!started) return
        source.captureCurrent()
    }
}
