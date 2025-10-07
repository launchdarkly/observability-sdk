package com.launchdarkly.observability.client

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.opentelemetry.sdk.common.CompletableResultCode
import io.opentelemetry.sdk.logs.data.LogRecordData
import io.opentelemetry.sdk.logs.export.LogRecordExporter
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class ConditionalLogRecordExporterTest {

    private lateinit var mockDelegate: LogRecordExporter
    private lateinit var conditionalLogRecordExporter: ConditionalLogRecordExporter

    @BeforeEach
    fun setup() {
        mockDelegate = mockk {
            every { export(any()) } returns CompletableResultCode.ofFailure()
        }
    }

    @Nested
    @DisplayName("Export Tests - Allow Both")
    inner class ExportTestsAllowBoth {

        @BeforeEach
        fun setup() {
            conditionalLogRecordExporter = ConditionalLogRecordExporter(
                delegate = mockDelegate,
                allowNormalLogs = true,
                allowCrashes = true
            )
        }

        @Test
        fun `should export both normal and crash logs when both are allowed`() {
            val normalLog = createMockLog("com.launchdarkly.observability")
            val crashLog = createMockLog("io.opentelemetry.crash")
            val logs = listOf(normalLog, crashLog)

            every { mockDelegate.export(logs) } returns CompletableResultCode.ofSuccess()

            val result = conditionalLogRecordExporter.export(logs)

            assertTrue(result.isSuccess)
            verify(exactly = 1) { mockDelegate.export(logs) }
        }
    }

    @Nested
    @DisplayName("Export Tests - Allow Normal Only")
    inner class ExportTestsAllowNormalOnly {

        @BeforeEach
        fun setup() {
            conditionalLogRecordExporter = ConditionalLogRecordExporter(
                delegate = mockDelegate,
                allowNormalLogs = true,
                allowCrashes = false
            )
        }

        @Test
        fun `should export only normal logs when crash logs are not allowed`() {
            val normalLog = createMockLog("com.launchdarkly.observability")
            val crashLog = createMockLog("io.opentelemetry.crash")

            every { mockDelegate.export(listOf(normalLog)) } returns CompletableResultCode.ofSuccess()

            val result = conditionalLogRecordExporter.export(listOf(normalLog, crashLog))

            assertTrue(result.isSuccess)
            verify(exactly = 1) { mockDelegate.export(listOf(normalLog)) }
        }

        @Test
        fun `should return success without calling delegate when only crash logs provided`() {
            val crashLog1 = createMockLog("io.opentelemetry.crash")
            val crashLog2 = createMockLog("io.opentelemetry.crash")

            val result = conditionalLogRecordExporter.export(listOf(crashLog1, crashLog2))

            assertTrue(result.isSuccess)
            verify(exactly = 0) { mockDelegate.export(any()) }
        }
    }

    @Nested
    @DisplayName("Export Tests - Allow Crashes Only")
    inner class ExportTestsAllowCrashesOnly {

        @BeforeEach
        fun setup() {
            conditionalLogRecordExporter = ConditionalLogRecordExporter(
                delegate = mockDelegate,
                allowNormalLogs = false,
                allowCrashes = true
            )
        }

        @Test
        fun `should export only crash logs when normal logs are not allowed`() {
            val normalLog = createMockLog("com.launchdarkly.observability")
            val crashLog = createMockLog("io.opentelemetry.crash")

            every { mockDelegate.export(listOf(crashLog)) } returns CompletableResultCode.ofSuccess()

            val result = conditionalLogRecordExporter.export(listOf(normalLog, crashLog))

            assertTrue(result.isSuccess)
            verify(exactly = 1) { mockDelegate.export(listOf(crashLog)) }
        }

        @Test
        fun `should return success without calling delegate when only normal logs provided`() {
            val normalLog1 = createMockLog("com.launchdarkly.observability")
            val normalLog2 = createMockLog("com.launchdarkly.observability")

            val result = conditionalLogRecordExporter.export(listOf(normalLog1, normalLog2))

            assertTrue(result.isSuccess)
            verify(exactly = 0) { mockDelegate.export(any()) }
        }
    }

    @Nested
    @DisplayName("Export Tests - Allow None")
    inner class ExportTestsAllowNone {

        @BeforeEach
        fun setup() {
            conditionalLogRecordExporter = ConditionalLogRecordExporter(
                delegate = mockDelegate,
                allowNormalLogs = false,
                allowCrashes = false
            )
        }

        @Test
        fun `should return success without calling delegate when no logs are allowed`() {
            val normalLog = createMockLog("com.launchdarkly.observability")
            val crashLog = createMockLog("io.opentelemetry.crash")

            val result = conditionalLogRecordExporter.export(listOf(normalLog, crashLog))

            assertTrue(result.isSuccess)
            verify(exactly = 0) { mockDelegate.export(any()) }
        }
    }

    @Nested
    @DisplayName("Export Tests - Edge Cases")
    inner class ExportTestsEdgeCases {

        @BeforeEach
        fun setup() {
            conditionalLogRecordExporter = ConditionalLogRecordExporter(
                delegate = mockDelegate,
                allowNormalLogs = true,
                allowCrashes = true
            )
        }

        @Test
        fun `should return success without calling delegate when empty collection provided`() {
            val emptyLogs = emptyList<LogRecordData>()

            val result = conditionalLogRecordExporter.export(emptyLogs)

            assertTrue(result.isSuccess)
            verify(exactly = 0) { mockDelegate.export(any()) }
        }

        @Test
        fun `should propagate delegate export failure`() {
            val logs = listOf(createMockLog("com.launchdarkly.observability"))

            every { mockDelegate.export(logs) } returns CompletableResultCode.ofFailure()

            val result = conditionalLogRecordExporter.export(logs)

            assertFalse(result.isSuccess)
            verify(exactly = 1) { mockDelegate.export(logs) }
        }
    }

    @Nested
    @DisplayName("Flush Tests")
    inner class FlushTests {

        @BeforeEach
        fun setup() {
            conditionalLogRecordExporter = ConditionalLogRecordExporter(
                delegate = mockDelegate,
                allowNormalLogs = true,
                allowCrashes = true
            )
        }

        @Test
        fun `should delegate flush to underlying exporter and propagate result`() {
            every { mockDelegate.flush() } returns CompletableResultCode.ofSuccess()

            val result = conditionalLogRecordExporter.flush()

            assertTrue(result.isSuccess)
            verify(exactly = 1) { mockDelegate.flush() }
        }
    }

    @Nested
    @DisplayName("Shutdown Tests")
    inner class ShutdownTests {

        @BeforeEach
        fun setup() {
            conditionalLogRecordExporter = ConditionalLogRecordExporter(
                delegate = mockDelegate,
                allowNormalLogs = true,
                allowCrashes = true
            )
        }

        @Test
        fun `should delegate shutdown to underlying exporter and propagate result`() {
            every { mockDelegate.shutdown() } returns CompletableResultCode.ofSuccess()

            val result = conditionalLogRecordExporter.shutdown()

            assertTrue(result.isSuccess)
            verify(exactly = 1) { mockDelegate.shutdown() }
        }
    }

    private fun createMockLog(
        instrumentationScopeName: String
    ): LogRecordData {
        return mockk<LogRecordData>().apply {
            every { instrumentationScopeInfo.name } returns instrumentationScopeName
        }
    }
}
