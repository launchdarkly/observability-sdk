package com.launchdarkly.observability.replay.plugin

import com.launchdarkly.observability.coroutines.DispatcherProviderHolder
import com.launchdarkly.sdk.ContextKind
import com.launchdarkly.sdk.LDContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Pure session-replay logic for identify events.
 *
 * Takes only simple JVM types — no Hook interface, no SDK-specific types.
 * Both [SessionReplayHook] (native Android SDK) and [SessionReplayHookProxy] (C# bridge)
 * delegate here so the replay logic is written exactly once.
 */
internal class SessionReplayHookExporter(
    private val plugin: SessionReplay
) {
    private val coroutineScope = CoroutineScope(DispatcherProviderHolder.current.default)

    fun afterIdentify(contextKeys: Map<String, String>, canonicalKey: String, completed: Boolean) {
        //System.out.println("LD:OBS:SessionReplay:afterIdentify contextKeys= $contextKeys, canonicalKey= $canonicalKey, completed= $completed")
        if (!completed) return

        val ldContext = buildLDContext(contextKeys)
        coroutineScope.launch {
            //System.out.println("LD:OBS:SessionReplay:afterIdentify plugin.replayInstrumentation= ${plugin.replayInstrumentation}")
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
