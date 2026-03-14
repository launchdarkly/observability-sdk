package com.launchdarkly.observability.replay.plugin

import com.launchdarkly.observability.coroutines.DispatcherProviderHolder
import com.launchdarkly.sdk.ContextKind
import com.launchdarkly.sdk.LDContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * JVM adapter for the C# / MAUI bridge.
 *
 * Accepts simple JVM types (String, Map) and delegates
 * to [SessionReplay] so the replay identify logic is accessible
 * from the Xamarin.Android binding.
 */
class SessionReplayHookProxy internal constructor(
    private val plugin: SessionReplay
) {
    private val coroutineScope = CoroutineScope(DispatcherProviderHolder.current.default)

    fun afterIdentify(contextKeys: Map<String, String>, canonicalKey: String, completed: Boolean) {
        if (!completed) return

        val ldContext = buildLDContext(contextKeys)
        coroutineScope.launch {
            plugin.replayInstrumentation?.identifySession(ldContext)
        }
    }

    private fun buildLDContext(contextKeys: Map<String, String>): LDContext {
        if (contextKeys.size == 1) {
            val (kind, key) = contextKeys.entries.first()
            return LDContext.create(ContextKind.of(kind), key)
        }
        val builder = LDContext.multiBuilder()
        for ((kind, key) in contextKeys) {
            builder.add(LDContext.create(ContextKind.of(kind), key))
        }
        return builder.build()
    }
}
