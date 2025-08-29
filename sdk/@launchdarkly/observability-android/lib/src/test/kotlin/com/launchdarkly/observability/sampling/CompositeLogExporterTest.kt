package com.launchdarkly.observability.sampling

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.opentelemetry.api.common.Value
import io.opentelemetry.sdk.common.CompletableResultCode
import io.opentelemetry.sdk.logs.data.LogRecordData
import io.opentelemetry.sdk.logs.export.LogRecordExporter
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested

class CompositeLogExporterTest {

    private lateinit var mockExporter1: LogRecordExporter
    private lateinit var mockExporter2: LogRecordExporter
    private lateinit var mockExporter3: LogRecordExporter

    @BeforeEach
    fun setup() {
        mockExporter1 = mockk()
        mockExporter2 = mockk()
        mockExporter3 = mockk()
    }

    @Nested
    @DisplayName("Constructor Tests")
    inner class ConstructorTests {

        @Test
        fun `should create with list constructor`() {
            val exporters = listOf(mockExporter1, mockExporter2)
            val compositeExporter = CompositeLogExporter(exporters)

            val logs = listOf(createMockLog("test"))

            every { mockExporter1.export(logs) } returns CompletableResultCode.ofSuccess()
            every { mockExporter2.export(logs) } returns CompletableResultCode.ofSuccess()

            val result = compositeExporter.export(logs)

            assertTrue(result.isSuccess)
            verify(exactly = 1) { mockExporter1.export(logs) }
            verify(exactly = 1) { mockExporter2.export(logs) }
        }

        @Test
        fun `should create with vararg constructor`() {
            val compositeExporter = CompositeLogExporter(mockExporter1, mockExporter2, mockExporter3)

            val logs = listOf(createMockLog("test"))

            every { mockExporter1.export(logs) } returns CompletableResultCode.ofSuccess()
            every { mockExporter2.export(logs) } returns CompletableResultCode.ofSuccess()
            every { mockExporter3.export(logs) } returns CompletableResultCode.ofSuccess()

            val result = compositeExporter.export(logs)

            assertTrue(result.isSuccess)
            verify(exactly = 1) { mockExporter1.export(logs) }
            verify(exactly = 1) { mockExporter2.export(logs) }
            verify(exactly = 1) { mockExporter3.export(logs) }
        }
    }

    @Nested
    @DisplayName("Export Tests")
    inner class ExportTests {

        @Test
        fun `should call all exporters on export`() {
            val compositeExporter = CompositeLogExporter(mockExporter1, mockExporter2)
            val logs = listOf(createMockLog("log1"), createMockLog("log2"))

            every { mockExporter1.export(logs) } returns CompletableResultCode.ofSuccess()
            every { mockExporter2.export(logs) } returns CompletableResultCode.ofSuccess()

            val result = compositeExporter.export(logs)

            assertTrue(result.isSuccess)
            verify(exactly = 1) { mockExporter1.export(logs) }
            verify(exactly = 1) { mockExporter2.export(logs) }
        }

        @Test
        fun `should return success when all exporters succeed`() {
            val compositeExporter = CompositeLogExporter(mockExporter1, mockExporter2)
            val logs = listOf(createMockLog("test"))

            every { mockExporter1.export(logs) } returns CompletableResultCode.ofSuccess()
            every { mockExporter2.export(logs) } returns CompletableResultCode.ofSuccess()

            val result = compositeExporter.export(logs)

            assertTrue(result.isSuccess)
        }

        @Test
        fun `should return failure when any exporter fails`() {
            val compositeExporter = CompositeLogExporter(mockExporter1, mockExporter2)
            val logs = listOf(createMockLog("test"))

            every { mockExporter1.export(logs) } returns CompletableResultCode.ofSuccess()
            every { mockExporter2.export(logs) } returns CompletableResultCode.ofFailure()

            val result = compositeExporter.export(logs)

            assertFalse(result.isSuccess)
        }

        @Test
        fun `should handle empty logs collection`() {
            val compositeExporter = CompositeLogExporter(mockExporter1, mockExporter2)
            val emptyLogs = emptyList<LogRecordData>()

            every { mockExporter1.export(emptyLogs) } returns CompletableResultCode.ofSuccess()
            every { mockExporter2.export(emptyLogs) } returns CompletableResultCode.ofSuccess()

            val result = compositeExporter.export(emptyLogs)

            assertTrue(result.isSuccess)
            verify(exactly = 1) { mockExporter1.export(emptyLogs) }
            verify(exactly = 1) { mockExporter2.export(emptyLogs) }
        }
    }

    @Nested
    @DisplayName("Flush Tests")
    inner class FlushTests {

        @Test
        fun `should call flush on all exporters`() {
            val compositeExporter = CompositeLogExporter(mockExporter1, mockExporter2)

            every { mockExporter1.flush() } returns CompletableResultCode.ofSuccess()
            every { mockExporter2.flush() } returns CompletableResultCode.ofSuccess()

            val result = compositeExporter.flush()

            assertTrue(result.isSuccess)
            verify(exactly = 1) { mockExporter1.flush() }
            verify(exactly = 1) { mockExporter2.flush() }
        }

        @Test
        fun `should return success when all exporters flush successfully`() {
            val compositeExporter = CompositeLogExporter(mockExporter1, mockExporter2)

            every { mockExporter1.flush() } returns CompletableResultCode.ofSuccess()
            every { mockExporter2.flush() } returns CompletableResultCode.ofSuccess()

            val result = compositeExporter.flush()

            assertTrue(result.isSuccess)
        }

        @Test
        fun `should return failure when any exporter flush fails`() {
            val compositeExporter = CompositeLogExporter(mockExporter1, mockExporter2)

            every { mockExporter1.flush() } returns CompletableResultCode.ofSuccess()
            every { mockExporter2.flush() } returns CompletableResultCode.ofFailure()

            val result = compositeExporter.flush()

            assertFalse(result.isSuccess)
        }
    }

    @Nested
    @DisplayName("Shutdown Tests")
    inner class ShutdownTests {

        @Test
        fun `should call shutdown on all exporters`() {
            val compositeExporter = CompositeLogExporter(mockExporter1, mockExporter2)

            every { mockExporter1.shutdown() } returns CompletableResultCode.ofSuccess()
            every { mockExporter2.shutdown() } returns CompletableResultCode.ofSuccess()

            val result = compositeExporter.shutdown()

            assertTrue(result.isSuccess)
            verify(exactly = 1) { mockExporter1.shutdown() }
            verify(exactly = 1) { mockExporter2.shutdown() }
        }

        @Test
        fun `should return success when all exporters shutdown successfully`() {
            val compositeExporter = CompositeLogExporter(mockExporter1, mockExporter2)

            every { mockExporter1.shutdown() } returns CompletableResultCode.ofSuccess()
            every { mockExporter2.shutdown() } returns CompletableResultCode.ofSuccess()

            val result = compositeExporter.shutdown()

            assertTrue(result.isSuccess)
        }

        @Test
        fun `should return failure when any exporter shutdown fails`() {
            val compositeExporter = CompositeLogExporter(mockExporter1, mockExporter2)

            every { mockExporter1.shutdown() } returns CompletableResultCode.ofSuccess()
            every { mockExporter2.shutdown() } returns CompletableResultCode.ofFailure()

            val result = compositeExporter.shutdown()

            assertFalse(result.isSuccess)
        }
    }

    private fun createMockLog(name: String): LogRecordData {
        return mockk<LogRecordData>().apply {
            every { bodyValue } returns Value.of(name)
            every { eventName } returns name
        }
    }
}
