package com.launchdarkly.observability.client.screen

import android.app.Application

/**
 * Orchestrates automatic screen-view capture.
 *
 * Registers an [ActivityScreenSource] on the [application] and forwards captured [ScreenView]s to
 * [onScreenView] (typically [com.launchdarkly.observability.client.ObservabilityService.emitScreenView]),
 * the single funnel shared with the manual `trackScreenView` API.
 */
internal class ScreenViewManager(
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
}
