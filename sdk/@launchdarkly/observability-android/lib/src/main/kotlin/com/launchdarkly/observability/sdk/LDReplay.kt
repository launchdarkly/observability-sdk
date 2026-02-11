package com.launchdarkly.observability.sdk

/**
 * LDReplay is the singleton entry point for controlling Session Replay capture.
 *
 * If Session Replay is not configured, these methods are no-ops.
 */
object LDReplay {
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
