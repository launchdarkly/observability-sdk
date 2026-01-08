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

    fun start() {
        synchronized(lock) {
            if (job != null) return

            val stream = Channel<Trigger>(Channel.CONFLATED).also { channel = it }

            job = scope.launch {
                launch {
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
    }

    fun stop() {
        synchronized(lock) {
            job?.cancel()
            job = null
            channel?.close()
            channel = null
            clearPending()
        }
    }

    fun flush() {
        doTrigger(Trigger.FLUSH)
    }

    private fun doTrigger(next: Trigger) {
        synchronized(lock) {
            val stream = channel ?: return
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
