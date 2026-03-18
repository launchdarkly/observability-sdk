package com.launchdarkly.observability.replay.plugin

/**
 * JVM adapter for the C# / MAUI bridge.
 *
 * Accepts simple JVM types (String, Map) and delegates
 * to [SessionReplayHookExporter] so the replay logic is written once.
 * The C# NativeHookProxy delegates here via the Xamarin.Android binding.
 */
class SessionReplayHookProxy internal constructor(
    private val exporter: SessionReplayHookExporter
) {
    fun afterIdentify(contextKeys: Map<String, String>, canonicalKey: String, completed: Boolean) {
        exporter.afterIdentify(contextKeys, canonicalKey, completed)
    }
}
