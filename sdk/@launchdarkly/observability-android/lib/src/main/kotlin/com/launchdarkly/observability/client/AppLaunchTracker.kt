package com.launchdarkly.observability.client

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.Process
import android.os.SystemClock

/**
 * Resolves the product-milestone of a launch (`install` / `update` / `relaunch`) and the cold/warm
 * startup dimension, then emits a single [AppLaunchSignal] on [start].
 *
 * The launch type is derived by comparing the current app version against the last one persisted in
 * [SharedPreferences]; the current version is recorded as a side effect so the next launch can be
 * classified.
 *
 * Mirrors [AppLifecycleTracker]: the signal is emitted unconditionally (so the Session Replay
 * `Launch` breadcrumb always fires); the `app_launch` span is gated separately by
 * [com.launchdarkly.observability.api.ObservabilityOptions.Analytics.appLaunch].
 *
 * @param onSignal invoked once with the resolved launch signal.
 * @param prefs version store; overridable for tests.
 */
class AppLaunchTracker(
    private val application: Application,
    private val onSignal: (AppLaunchSignal) -> Unit,
    private val prefs: SharedPreferences =
        application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE),
) {
    private var emitted = false

    fun start() {
        if (emitted) return
        emitted = true
        onSignal(resolveSignal())
    }

    private fun resolveSignal(): AppLaunchSignal {
        val (version, build) = resolveVersion()

        val stored = prefs.getString(KEY_LAST_VERSION, null)
        val launchType = classify(stored, version)
        val previousVersion = if (launchType == AppLaunchSignal.LaunchType.UPDATE) stored else null
        version?.let { prefs.edit().putString(KEY_LAST_VERSION, it).apply() }

        // On Android the SDK initializes from a freshly created process, so an `app_launch` is a cold
        // process start. (Warm/hot relate to activity recreation, captured by `app_foreground`.)
        val startDurationMs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            (SystemClock.uptimeMillis() - Process.getStartUptimeMillis()).takeIf { it >= 0 }
        } else {
            null
        }

        return AppLaunchSignal(
            launchType = launchType,
            version = version,
            build = build,
            previousVersion = previousVersion,
            startType = AppLaunchSignal.StartType.COLD,
            startDurationMs = startDurationMs,
        )
    }

    private fun resolveVersion(): Pair<String?, String?> {
        return try {
            val info = application.packageManager.getPackageInfo(application.packageName, 0)
            val build = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                info.longVersionCode.toString()
            } else {
                @Suppress("DEPRECATION")
                info.versionCode.toString()
            }
            info.versionName to build
        } catch (_: Exception) {
            null to null
        }
    }

    companion object {
        const val PREFS_NAME = "com.launchdarkly.observability"
        const val KEY_LAST_VERSION = "lastAppVersion"

        /** Classifies a launch relative to the stored version. Pure, for testability. */
        fun classify(storedVersion: String?, currentVersion: String?): AppLaunchSignal.LaunchType =
            when {
                // Without a readable version there is nothing to persist or compare, so the
                // milestone is indeterminable. Returning UNKNOWN (rather than INSTALL) avoids
                // misclassifying every such relaunch as a fresh install.
                currentVersion == null -> AppLaunchSignal.LaunchType.UNKNOWN
                storedVersion == null -> AppLaunchSignal.LaunchType.INSTALL
                storedVersion != currentVersion -> AppLaunchSignal.LaunchType.UPDATE
                else -> AppLaunchSignal.LaunchType.RELAUNCH
            }
    }
}
