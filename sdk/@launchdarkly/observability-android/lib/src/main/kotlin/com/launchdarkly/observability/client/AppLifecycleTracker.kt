package com.launchdarkly.observability.client

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner

/**
 * OTel-aligned lifecycle state values carried under `event.lifecycle_state`.
 *
 * See the analytics taxonomy (`app_foreground` / `app_background`).
 */
internal enum class AppLifecycleState(val wireValue: String) {
    FOREGROUND("foreground"),
    BACKGROUND("background"),
}

/**
 * Derives app-lifecycle analytics events ([AppLifecycleSignal]) from process lifecycle callbacks,
 * emitting taxonomy-aligned signals (consumed as both spans and Session Replay breadcrumbs).
 *
 * Mapping:
 * - process `ON_START` (app entered foreground) -> [AppLifecycleSignal.Kind.FOREGROUND]
 * - process `ON_STOP`  (app entered background) -> [AppLifecycleSignal.Kind.BACKGROUND]
 *
 * Registering a [androidx.lifecycle.LifecycleObserver] synchronously replays the current state to
 * the new observer. That catch-up `onStart` is suppressed so starting the tracker while the app is
 * already foregrounded does not emit a spurious foreground (only genuine transitions are reported).
 *
 * Must be started on the main thread.
 *
 * @param onSignal invoked for each foreground/background transition.
 * @param lifecycleOwnerProvider source of the process-wide lifecycle; overridable for tests.
 */
class AppLifecycleTracker(
    private val onSignal: (AppLifecycleSignal) -> Unit,
    private val lifecycleOwnerProvider: () -> LifecycleOwner = { ProcessLifecycleOwner.get() },
) {
    private var started = false
    private var suppressInitialStart = false

    private val observer = object : DefaultLifecycleObserver {
        override fun onStart(owner: LifecycleOwner) {
            if (suppressInitialStart) return
            onSignal(
                AppLifecycleSignal(
                    kind = AppLifecycleSignal.Kind.FOREGROUND,
                    lifecycleState = AppLifecycleState.FOREGROUND.wireValue,
                )
            )
        }

        override fun onStop(owner: LifecycleOwner) {
            onSignal(
                AppLifecycleSignal(
                    kind = AppLifecycleSignal.Kind.BACKGROUND,
                    lifecycleState = AppLifecycleState.BACKGROUND.wireValue,
                )
            )
        }
    }

    fun start() {
        if (started) return
        // addObserver synchronously replays the current lifecycle state to the new observer; mark
        // that window so the catch-up onStart (when already foregrounded) is not reported as a real
        // foreground transition.
        suppressInitialStart = true
        lifecycleOwnerProvider().lifecycle.addObserver(observer)
        suppressInitialStart = false
        started = true
    }

    fun stop() {
        if (!started) return
        lifecycleOwnerProvider().lifecycle.removeObserver(observer)
        started = false
    }
}
