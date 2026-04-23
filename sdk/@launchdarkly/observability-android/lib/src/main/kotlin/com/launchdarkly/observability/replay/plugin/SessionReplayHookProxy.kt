package com.launchdarkly.observability.replay.plugin

import com.launchdarkly.observability.sdk.SessionReplayServicing

/**
 * JVM adapter for cross-platform bridges (C# / MAUI, React Native, etc.).
 *
 * Accepts simple JVM types (String, Map) and delegates
 * to [SessionReplayServicing] so the replay logic is written once.
 * The C# NativeHookProxy delegates here via the Xamarin.Android binding.
 * The React Native turbo module delegates here via LDReplay.hookProxy.
 */
class SessionReplayHookProxy internal constructor(
    private val sessionReplayService: SessionReplayServicing
) {
    fun afterIdentify(contextKeys: Map<String, String>, canonicalKey: String, completed: Boolean) {
        sessionReplayService.afterIdentify(contextKeys, canonicalKey, completed)
    }
}
