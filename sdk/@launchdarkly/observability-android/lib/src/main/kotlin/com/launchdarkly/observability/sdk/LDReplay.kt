package com.launchdarkly.observability.sdk

import com.launchdarkly.observability.replay.plugin.SessionReplayHookProxy

/**
 * LDReplay is the singleton entry point for controlling Session Replay capture.
 *
 * If Session Replay is not configured, these methods are no-ops.
 */
object LDReplay {
    /**
     * Hook proxy for the C# / MAUI bridge. Set by the SessionReplay plugin during getHooks().
     */
    @Volatile
    var hookProxy: SessionReplayHookProxy? = null
        internal set

    @Volatile
    private var delegate: ReplayControl = object : ReplayControl {
        override fun start() {}
        override fun stop() {}
        override fun flush() {}
    }

    /**
     * Wires LDReplay to the active Session Replay controller.
     */
    internal fun init(controller: ReplayControl) {
        delegate = controller
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

internal interface ReplayControl {
    fun start()
    fun stop()
    fun flush()
}
