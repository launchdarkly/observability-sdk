package com.launchdarkly.observability.replay.transport

import android.os.SystemClock
import com.launchdarkly.logging.LDLogger
import com.launchdarkly.observability.coroutines.DispatcherProvider
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BatchWorkerTest {

    @Test
    fun `start triggers export on tick`() = runTest {
        mockkStatic(SystemClock::class)
        every { SystemClock.elapsedRealtime() } returns 0L

        val dispatcher = StandardTestDispatcher(testScheduler)
        val dispatcherProvider = TestDispatcherProvider(dispatcher)
        val logger = mockk<LDLogger>(relaxed = true)
        val eventQueue = EventQueue()

        val worker = BatchWorker(eventQueue, logger, dispatcherProvider)
        val exporter = RecordingExporter()
        worker.addExporter(exporter)

        eventQueue.send(TestPayload(timestamp = 0L, exporterClass = exporter::class.java))

        worker.start()
        advanceTimeBy(INTERVAL_MS)
        runCurrent()

        worker.stop()
        runCurrent()

        assertEquals(1, exporter.exported.size)
        unmockkStatic(SystemClock::class)
    }

    @Test
    fun `start removes items if exporter is null`() = runTest {
        mockkStatic(SystemClock::class)
        every { SystemClock.elapsedRealtime() } returns 0L

        val dispatcher = StandardTestDispatcher(testScheduler)
        val dispatcherProvider = TestDispatcherProvider(dispatcher)
        val logger = mockk<LDLogger>(relaxed = true)
        val eventQueue = EventQueue()
        val worker = BatchWorker(eventQueue, logger, dispatcherProvider)

        eventQueue.send(TestPayload(timestamp = 0L, exporterClass = RecordingExporter::class.java))

        // First tick triggers export while exporter is null which causes BatchWorker to remove the items
        worker.start()
        advanceTimeBy(INTERVAL_MS)
        runCurrent()

        val exporter = RecordingExporter()
        worker.addExporter(exporter)

        // Second tick triggers export with an exporter set but there are no items at this point
        advanceTimeBy(INTERVAL_MS)
        runCurrent()
        worker.stop()
        runCurrent()

        assertTrue(exporter.exported.isEmpty())
        unmockkStatic(SystemClock::class)
    }

    @Test
    fun `failed export applies backoff before retrying`() = runTest {
        mockkStatic(SystemClock::class)
        every { SystemClock.elapsedRealtime() } returns 0L

        val dispatcher = StandardTestDispatcher(testScheduler)
        val dispatcherProvider = TestDispatcherProvider(dispatcher)
        val logger = mockk<LDLogger>(relaxed = true)
        val eventQueue = EventQueue()
        val worker = BatchWorker(eventQueue, logger, dispatcherProvider)

        val exporter = RecordingExporter()
        exporter.shouldFail = true
        worker.addExporter(exporter)

        eventQueue.send(TestPayload(timestamp = 0L, exporterClass = RecordingExporter::class.java))

        // First tick triggers export with error resulting in reschedule the export adding a backoff time
        worker.start()
        advanceTimeBy(INTERVAL_MS)
        runCurrent()

        assertTrue(exporter.exported.isEmpty())

        exporter.shouldFail = false

        every { SystemClock.elapsedRealtime() } returns 2400 // Max back off time possible in second attempt using current default values
        advanceTimeBy(INTERVAL_MS)
        runCurrent()
        worker.stop()
        runCurrent()

        assertEquals(1, exporter.exported.size)
        unmockkStatic(SystemClock::class)
    }

    @Test
    fun `flush exports immediately`() = runTest {
        mockkStatic(SystemClock::class)
        every { SystemClock.elapsedRealtime() } returns 0L

        val dispatcher = StandardTestDispatcher(testScheduler)
        val eventQueue = EventQueue()
        val worker = BatchWorker(eventQueue, mockk(relaxed = true), TestDispatcherProvider(dispatcher))
        val exporter = RecordingExporter()
        worker.addExporter(exporter)

        eventQueue.send(TestPayload(timestamp = 0L, exporterClass = exporter::class.java))

        worker.start()
        worker.flush()
        runCurrent()

        worker.stop()
        runCurrent()

        assertEquals(1, exporter.exported.size)
        unmockkStatic(SystemClock::class)
    }

    @Test
    fun `stop prevents export`() = runTest {
        mockkStatic(SystemClock::class)
        every { SystemClock.elapsedRealtime() } returns 0L

        val dispatcher = StandardTestDispatcher(testScheduler)
        val eventQueue = EventQueue()
        val worker = BatchWorker(eventQueue, mockk(relaxed = true), TestDispatcherProvider(dispatcher))
        val exporter = RecordingExporter()
        worker.addExporter(exporter)

        eventQueue.send(TestPayload(timestamp = 0L, exporterClass = exporter::class.java))

        worker.start()
        worker.stop()
        worker.flush()
        advanceTimeBy(INTERVAL_MS)
        runCurrent()

        assertTrue(exporter.exported.isEmpty())
        unmockkStatic(SystemClock::class)
    }

    private class TestDispatcherProvider(
        dispatcher: CoroutineDispatcher,
    ) : DispatcherProvider {
        override val main: CoroutineDispatcher = dispatcher
        override val io: CoroutineDispatcher = dispatcher
        override val default: CoroutineDispatcher = dispatcher
        override val unconfined: CoroutineDispatcher = dispatcher
    }

    private class RecordingExporter : EventExporting {
        val exported = mutableListOf<List<EventQueueItem>>()
        var shouldFail = false

        override suspend fun export(items: List<EventQueueItem>) {
            if (shouldFail) {
                throw Exception()
            } else {
                exported.add(items)
            }
        }
    }

    private data class TestPayload(
        override val timestamp: Long,
        override val exporterClass: Class<out EventExporting>,
        private val payloadCost: Int = DEFAULT_COST,
    ) : EventQueueItemPayload {
        override fun cost(): Int = payloadCost
    }

    private companion object {
        private const val DEFAULT_COST = 1
        private const val INTERVAL_MS = 1_500L
    }
}
