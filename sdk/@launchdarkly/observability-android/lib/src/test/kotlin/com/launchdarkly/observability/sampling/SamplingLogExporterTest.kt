package com.launchdarkly.observability.sampling

import com.launchdarkly.observability.sampling.utils.MockExportSampler
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import io.opentelemetry.api.common.Value
import io.opentelemetry.exporter.otlp.http.logs.OtlpHttpLogRecordExporter
import io.opentelemetry.sdk.common.CompletableResultCode
import io.opentelemetry.sdk.logs.data.LogRecordData
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested

class SamplingLogExporterTest {

    private lateinit var mockDelegate: OtlpHttpLogRecordExporter
    private lateinit var mockSampler: ExportSampler
    private lateinit var samplingLogExporter: SamplingLogExporter

    @BeforeEach
    fun setup() {
        mockDelegate = mockk()
        mockSampler = MockExportSampler()
        samplingLogExporter = SamplingLogExporter(mockDelegate, mockSampler)
    }

    @Nested
    @DisplayName("Export Tests")
    inner class ExportTests {

        @Test
        fun `should return success when no logs are left to export after sampling`() {
            val logs = listOf(
                createMockLog("log1"),
                createMockLog("log2"),
                createMockLog("log3")
            )

            mockkStatic("com.launchdarkly.observability.sampling.SampleLogsKt")
            every { sampleLogs(logs, mockSampler) } returns emptyList()

            val expectedResult = CompletableResultCode.ofSuccess()

            val result = samplingLogExporter.export(logs)

            assertEquals(expectedResult.isDone, result.isDone)
            assertTrue(result.isSuccess)

            // Verify delegate export was never called
            verify(exactly = 0) { mockDelegate.export(any()) }

            unmockkStatic("com.launchdarkly.observability.sampling.SampleLogsKt")
        }

        @Test
        fun `should delegate to underlying exporter when logs are sampled`() {
            val originalLogs = listOf(
                createMockLog("log1"),
                createMockLog("log2"),
                createMockLog("log3")
            )

            val sampledLogs = listOf(
                createMockLog("log1"),
                createMockLog("log3")
            )

            mockkStatic("com.launchdarkly.observability.sampling.SampleLogsKt")
            every { sampleLogs(originalLogs, mockSampler) } returns sampledLogs

            val expectedResult = CompletableResultCode.ofSuccess()
            every { mockDelegate.export(sampledLogs) } returns expectedResult

            val result = samplingLogExporter.export(originalLogs)

            assertEquals(expectedResult, result)

            // Verify delegate export was called with sampled logs
            verify(exactly = 1) { mockDelegate.export(sampledLogs) }

            unmockkStatic("com.launchdarkly.observability.sampling.SampleLogsKt")
        }

        @Test
        fun `should handle empty collection`() {
            val emptyLogs = emptyList<LogRecordData>()

            mockkStatic("com.launchdarkly.observability.sampling.SampleLogsKt")
            every { sampleLogs(emptyLogs, mockSampler) } returns emptyList()

            val result = samplingLogExporter.export(emptyLogs)

            assertTrue(result.isSuccess)
            verify(exactly = 0) { mockDelegate.export(any()) }

            unmockkStatic("com.launchdarkly.observability.sampling.SampleLogsKt")
        }

        @Test
        fun `should propagate delegate export failure`() {
            val originalLogs = listOf(createMockLog("log1"))
            val sampledLogs = listOf(createMockLog("log1"))

            mockkStatic("com.launchdarkly.observability.sampling.SampleLogsKt")
            every { sampleLogs(originalLogs, mockSampler) } returns sampledLogs

            val failedResult = CompletableResultCode.ofFailure()
            every { mockDelegate.export(sampledLogs) } returns failedResult

            val result = samplingLogExporter.export(originalLogs)

            assertEquals(failedResult, result)
            assertFalse(result.isSuccess)

            unmockkStatic("com.launchdarkly.observability.sampling.SampleLogsKt")
        }
    }

    @Nested
    @DisplayName("Flush Tests")
    inner class FlushTests {

        @Test
        fun `should delegate flush to underlying exporter and propagate result`() {
            val expectedResult = CompletableResultCode.ofSuccess()
            every { mockDelegate.flush() } returns expectedResult

            val result = samplingLogExporter.flush()

            assertEquals(expectedResult, result)
            verify(exactly = 1) { mockDelegate.flush() }
        }
    }

    @Nested
    @DisplayName("Shutdown Tests")
    inner class ShutdownTests {

        @Test
        fun `should delegate shutdown to underlying exporter and propagate result`() {
            val expectedResult = CompletableResultCode.ofSuccess()
            every { mockDelegate.shutdown() } returns expectedResult

            val result = samplingLogExporter.shutdown()

            assertEquals(expectedResult, result)
            verify(exactly = 1) { mockDelegate.shutdown() }
        }
    }

    private fun createMockLog(name: String): LogRecordData {
        return mockk<LogRecordData>().apply {
            every { bodyValue } returns Value.of(name)
            every { eventName } returns name
        }
    }
}
