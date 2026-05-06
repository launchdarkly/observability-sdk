package com.launchdarkly.observability.logs

import com.launchdarkly.observability.replay.transport.EventQueue
import io.mockk.every
import io.mockk.mockk
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.context.Context
import io.opentelemetry.sdk.logs.ReadWriteLogRecord
import io.opentelemetry.sdk.testing.logs.TestLogRecordData
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class EventLogRecordProcessorTest {

    @Test
    fun `onEmit enqueues a LogItemPayload into the shared queue`() {
        val queue = EventQueue()
        val processor = EventLogRecordProcessor(eventQueue = queue)

        val record = TestLogRecordData.builder()
            .setTimestamp(1_700_000_000_000_000_000L, java.util.concurrent.TimeUnit.NANOSECONDS)
            .setAttributes(Attributes.empty())
            .build()
        val readWrite = mockk<ReadWriteLogRecord> { every { toLogRecordData() } returns record }

        processor.onEmit(Context.root(), readWrite)

        val batch = queue.earliest(
            costBudget = Int.MAX_VALUE,
            limit = 10,
            except = emptySet(),
        )
        assertTrue(batch != null, "batch should be non-null")
        assertEquals(OtlpLogExporter::class.java, batch!!.exporterClass)
        assertEquals(1, batch.items.size)

        val payload = batch.items[0].payload as LogItemPayload
        assertSame(record, payload.logRecord)
    }

    @Test
    fun `forceFlush returns success even without a BatchWorker`() {
        val processor = EventLogRecordProcessor(eventQueue = EventQueue())
        assertTrue(processor.forceFlush().isSuccess)
    }

    @Test
    fun `shutdown returns success even without a BatchWorker`() {
        val processor = EventLogRecordProcessor(eventQueue = EventQueue())
        assertTrue(processor.shutdown().isSuccess)
    }
}
