package com.launchdarkly.observability.sdk

import com.launchdarkly.observability.replay.plugin.SessionReplayHookProxy

/**
 * LDReplay is the singleton entry point for controlling Session Replay capture.
 *
 * If Session Replay is not configured, these methods are no-ops.
 */
object LDReplay {
    @Volatile
    internal var client: SessionReplayServicing? = null

    /**
     * Hook proxy for the C# / MAUI bridge.
     */
    val hookProxy: SessionReplayHookProxy?
        get() = client?.let { SessionReplayHookProxy(it) }

    @Volatile
    private var delegate: SessionReplayServicing = object : SessionReplayServicing {
        override fun start() {}
        override fun stop() {}
        override fun flush() {}
        override fun afterIdentify(contextKeys: Map<String, String>, canonicalKey: String, completed: Boolean) {}
    }

    /**
     * Wires LDReplay to the active Session Replay controller.
     */
    internal fun init(controller: SessionReplayServicing) {
        delegate = controller
        client = controller
    }

    /**
     * Starts session replay capture
     */
    fun start() {
        delegate.start()
    }

    /**
     * Stops session replay capture
     */
    fun stop() {
        delegate.stop()
    }

    /**
     * Flushes any queued replay events immediately.
     */
    fun flush() {
        delegate.flush()
    }
}

internal interface SessionReplayServicing {
    fun start()
    fun stop()
    fun flush()
    fun afterIdentify(contextKeys: Map<String, String>, canonicalKey: String, completed: Boolean)
}
