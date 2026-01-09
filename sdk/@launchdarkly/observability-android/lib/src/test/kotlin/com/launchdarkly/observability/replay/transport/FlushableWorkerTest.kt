package com.launchdarkly.observability.replay.transport

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FlushableWorkerTest {

    @Test
    fun `flush triggers work with flush flag`() = runTest {
        val calls = mutableListOf<Boolean>()
        val worker = FlushableWorker(
            intervalMillis = INTERVAL_MS,
            scope = this,
            work = { calls.add(it) },
        )

        worker.start()
        worker.flush()
        runCurrent()
        worker.stop()

        assertEquals(listOf(true), calls)
    }

    @Test
    fun `tick triggers work at interval`() = runTest {
        val calls = mutableListOf<Boolean>()
        val worker = FlushableWorker(
            intervalMillis = INTERVAL_MS,
            scope = this,
            work = { calls.add(it) },
        )

        worker.start()
        advanceTimeBy(INTERVAL_MS)
        runCurrent()
        worker.stop()

        assertEquals(listOf(false), calls)
    }

    @Test
    fun `start is idempotent`() = runTest {
        val calls = mutableListOf<Boolean>()
        val worker = FlushableWorker(
            intervalMillis = INTERVAL_MS,
            scope = this,
            work = { calls.add(it) },
        )

        worker.start()
        worker.start()
        advanceTimeBy(INTERVAL_MS)
        runCurrent()
        worker.stop()

        assertEquals(1, calls.size)
    }

    @Test
    fun `stop cancels ticker and ignores flush`() = runTest {
        val calls = mutableListOf<Boolean>()
        val worker = FlushableWorker(
            intervalMillis = INTERVAL_MS,
            scope = this,
            work = { calls.add(it) },
        )

        worker.start()
        worker.stop()
        worker.flush()
        advanceTimeBy(INTERVAL_MS)
        runCurrent()

        assertTrue(calls.isEmpty())
    }

    @Test
    fun `multiple flush calls while work in progress are merged`() = runTest {
        val calls = mutableListOf<Boolean>()
        val started = CompletableDeferred<Unit>()
        val gate = CompletableDeferred<Unit>()
        val worker = FlushableWorker(
            intervalMillis = INTERVAL_MS,
            scope = this,
            work = { isFlush ->
                calls.add(isFlush)
                if (!started.isCompleted) started.complete(Unit)
                gate.await()
            },
        )

        worker.start()
        worker.flush()
        runCurrent()
        started.await()

        worker.flush()
        worker.flush()
        runCurrent()

        gate.complete(Unit)
        runCurrent()
        worker.stop()

        assertEquals(listOf(true), calls)
    }

    @Test
    fun `concurrent flush calls while work in progress are merged`() = runTest {
        val calls = mutableListOf<Boolean>()
        val started = CompletableDeferred<Unit>()
        val gate = CompletableDeferred<Unit>()
        val worker = FlushableWorker(
            intervalMillis = INTERVAL_MS,
            scope = this,
            work = { isFlush ->
                calls.add(isFlush)
                if (!started.isCompleted) started.complete(Unit)
                gate.await()
            },
        )

        // 1) Start the worker and trigger an initial flush that blocks inside work.
        worker.start()
        worker.flush()
        runCurrent()
        started.await()

        // 2) While work is blocked, issue many concurrent flush calls.
        withContext(Dispatchers.Default) {
            coroutineScope {
                repeat(50) {
                    launch {
                        repeat(100) {
                            worker.flush()
                        }
                    }
                }
            }
        }

        // 3) Release the blocked work and process any pending triggers.
        gate.complete(Unit)
        runCurrent()
        worker.stop()

        // 4) Only the first flush should have been processed; the rest are merged.
        assertEquals(listOf(true), calls)
    }

    @Test
    fun `flush queued during tick work runs after tick`() = runTest {
        val calls = mutableListOf<Boolean>()
        val started = CompletableDeferred<Unit>()
        val gate = CompletableDeferred<Unit>()
        val worker = FlushableWorker(
            intervalMillis = INTERVAL_MS,
            scope = this,
            work = { isFlush ->
                calls.add(isFlush)
                if (!started.isCompleted) started.complete(Unit)
                gate.await()
            },
        )

        worker.start()
        advanceTimeBy(INTERVAL_MS)
        runCurrent()
        started.await()

        worker.flush()
        runCurrent()

        gate.complete(Unit)
        runCurrent()
        worker.stop()

        assertEquals(listOf(false, true), calls)
    }


    /**
     * Stress test concurrent start/stop/flush calls and verify the worker still processes a flush afterward.
     */
    @Test
    fun `concurrent start, stop, flush, leaves worker functional`() = runTest {
        val calls = mutableListOf<Boolean>()
        val worker = FlushableWorker(
            intervalMillis = INTERVAL_MS,
            scope = this,
            work = { calls.add(it) },
        )

        withContext(Dispatchers.Default) {
            coroutineScope {
                repeat(50) { index ->
                    launch {
                        repeat(100) { iteration ->
                            when ((index + iteration) % 3) {
                                0 -> worker.start()
                                1 -> worker.flush()
                                else -> worker.stop()
                            }
                        }
                    }
                }
            }
        }

        worker.stop()
        runCurrent()

        val countBefore = calls.size
        worker.start()
        worker.flush()
        runCurrent()
        worker.stop()

        assertEquals(countBefore + 1, calls.size)
        assertTrue(calls.last())
    }

    private companion object {
        private const val INTERVAL_MS = 1_000L
    }
}
