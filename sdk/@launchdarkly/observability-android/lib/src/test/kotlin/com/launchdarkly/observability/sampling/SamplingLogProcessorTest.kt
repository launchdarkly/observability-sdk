package com.launchdarkly.observability.sampling

import com.launchdarkly.observability.sampling.utils.FakeExportSampler
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.context.Context
import io.opentelemetry.sdk.common.CompletableResultCode
import io.opentelemetry.sdk.logs.LogRecordProcessor
import io.opentelemetry.sdk.logs.ReadWriteLogRecord
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SamplingLogProcessorTest {

    @Test
    fun `should delegate log when sampling is disabled`() {
        val delegate = mockk<LogRecordProcessor>(relaxUnitFun = true)
        val logRecord = mockk<ReadWriteLogRecord>()
        val sampler = FakeExportSampler(isSamplingEnabled = { false })

        val processor = SamplingLogProcessor(delegate, sampler)
        val context = Context.root()

        processor.onEmit(context, logRecord)

        verify(exactly = 1) { delegate.onEmit(context, logRecord) }
    }

    @Test
    fun `should drop log when sampler decides not to sample`() {
        val delegate = mockk<LogRecordProcessor>()
        val logRecord = mockk<ReadWriteLogRecord> {
            every { toLogRecordData() } returns mockk()
        }
        val sampler = FakeExportSampler(sampleLog = { SamplingResult(sample = false) })

        val processor = SamplingLogProcessor(delegate, sampler)

        processor.onEmit(Context.root(), logRecord)

        verify(exactly = 0) { delegate.onEmit(any(), any()) }
        verify(exactly = 0) { logRecord.setAllAttributes(any()) }
    }

    @Test
    fun `should merge sampling attributes before delegating`() {
        val delegate = mockk<LogRecordProcessor>(relaxUnitFun = true)
        val existingAttributes = Attributes.builder()
            .put("existing.key", "existingValue")
            .build()
        val samplingAttributes = Attributes.builder()
            .put("sampling.key", 1L)
            .build()

        val logRecord = mockk<ReadWriteLogRecord> {
            every { attributes } returns existingAttributes
            every { toLogRecordData() } returns mockk()
            every { setAllAttributes(any()) } returns this
        }

        val sampler = FakeExportSampler(
            sampleLog = { SamplingResult(sample = true, attributes = samplingAttributes) }
        )
        val processor = SamplingLogProcessor(delegate, sampler)
        val context = Context.root()

        processor.onEmit(context, logRecord)

        val expectedAttributes = Attributes.builder()
            .putAll(existingAttributes)
            .putAll(samplingAttributes)
            .build()

        verify(exactly = 1) {
            logRecord.setAllAttributes(withArg { mergedAttrs ->
                assertEquals(expectedAttributes, mergedAttrs)
            })
        }
        verify(exactly = 1) { delegate.onEmit(context, logRecord) }
    }

    @Test
    fun `should delegate without adding attributes when sampler returns none`() {
        val delegate = mockk<LogRecordProcessor>(relaxUnitFun = true)
        val logRecord = mockk<ReadWriteLogRecord> {
            every { toLogRecordData() } returns mockk()
        }

        val sampler = FakeExportSampler(sampleLog = { SamplingResult(sample = true) })
        val processor = SamplingLogProcessor(delegate, sampler)
        val context = Context.root()

        processor.onEmit(context, logRecord)

        verify(exactly = 1) { delegate.onEmit(context, logRecord) }
        verify(exactly = 0) { logRecord.setAllAttributes(any()) }
    }

    @Test
    fun `forceFlush should delegate to underlying processor`() {
        val expectedResult = CompletableResultCode.ofSuccess()
        val delegate = mockk<LogRecordProcessor> {
            every { forceFlush() } returns expectedResult
        }
        val processor = SamplingLogProcessor(delegate, FakeExportSampler())

        val result = processor.forceFlush()

        assertEquals(expectedResult, result)
        verify(exactly = 1) { delegate.forceFlush() }
    }

    @Test
    fun `shutdown should delegate to underlying processor`() {
        val expectedResult = CompletableResultCode.ofSuccess()
        val delegate = mockk<LogRecordProcessor> {
            every { shutdown() } returns expectedResult
        }
        val processor = SamplingLogProcessor(delegate, FakeExportSampler())

        val result = processor.shutdown()

        assertEquals(expectedResult, result)
        verify(exactly = 1) { delegate.shutdown() }
    }

    @Test
    fun `close should delegate to underlying processor`() {
        val delegate = mockk<LogRecordProcessor>(relaxUnitFun = true)
        val processor = SamplingLogProcessor(delegate, FakeExportSampler())

        processor.close()

        verify(exactly = 1) { delegate.close() }
    }
}
