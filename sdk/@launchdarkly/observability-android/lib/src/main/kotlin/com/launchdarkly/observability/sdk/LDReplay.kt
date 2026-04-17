package com.launchdarkly.observability.sdk

/**
 * LDReplay is the singleton entry point for controlling Session Replay capture.
 *
 * If Session Replay is not configured, these methods are no-ops.
 *
 * Internal SDK state and APIs that are not part of the customer-facing surface
 * live on [LDReplayInternal].
 */
object LDReplay {
    /**
     * Starts session replay capture.
     */
    fun start() = LDReplayInternal.delegate.start()

    /**
     * Stops session replay capture.
     */
    fun stop() = LDReplayInternal.delegate.stop()

    /**
     * Flushes any queued replay events immediately.
     */
    fun flush() = LDReplayInternal.delegate.flush()
}
