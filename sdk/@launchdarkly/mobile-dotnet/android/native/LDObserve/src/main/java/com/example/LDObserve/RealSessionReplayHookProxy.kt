package com.launchdarkly.LDNative

import com.launchdarkly.observability.replay.plugin.SessionReplayHookProxy as PluginSessionReplayHookProxy

/**
 * Bindable wrapper around the real session replay hook proxy.
 *
 * Keeping this class in the LDNative package ensures Xamarin binding generation
 * emits a C# type without needing manual JNI glue code.
 */
class RealSessionReplayHookProxy internal constructor(
    private val delegate: PluginSessionReplayHookProxy
) {
    fun afterIdentify(contextKeys: Map<String, String>, canonicalKey: String, completed: Boolean) {
        delegate.afterIdentify(contextKeys, canonicalKey, completed)
    }
}
