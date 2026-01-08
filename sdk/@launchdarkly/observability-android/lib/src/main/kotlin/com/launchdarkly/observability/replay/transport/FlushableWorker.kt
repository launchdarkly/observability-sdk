package com.launchdarkly.observability.replay.transport

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

internal class FlushableWorker(
    private val intervalMillis: Long,
    private val scope: CoroutineScope,
    private val work: suspend (Boolean) -> Unit,
) {
    private enum class Trigger {
        TICK,
        FLUSH,
    }

    private val lock = Any()
    private var pending: Trigger? = null
    private var channel: Channel<Trigger>? = null
    private var job: Job? = null
    private var tickJob: Job? = null

    fun start() {
        if (job != null) return

        val stream = Channel<Trigger>(Channel.CONFLATED)
        channel = stream

        job = scope.launch {
            tickJob = scope.launch {
                while (isActive) {
                    delay(intervalMillis)
                    doTrigger(Trigger.TICK)
                }
            }

            for (trigger in stream) {
                work(trigger == Trigger.FLUSH)
                clearPending()
            }
        }
    }

    fun stop() {
        tickJob?.cancel()
        tickJob = null
        job?.cancel()
        job = null
        channel?.close()
        channel = null
        clearPending()
    }

    fun flush() {
        doTrigger(Trigger.FLUSH)
    }

    private fun doTrigger(next: Trigger) {
        val stream = channel ?: return
        synchronized(lock) {
            if (pending == Trigger.FLUSH) {
                return
            }
            if (next == Trigger.FLUSH || pending == null) {
                pending = next
                stream.trySend(next)
            }
        }
    }

    private fun clearPending() {
        synchronized(lock) {
            pending = null
        }
    }
}
