package com.launchdarkly.observability.client

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.opentelemetry.sdk.common.CompletableResultCode
import io.opentelemetry.sdk.trace.data.SpanData
import io.opentelemetry.sdk.trace.export.SpanExporter
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class ConditionalSpanExporterTest {

    private lateinit var mockDelegate: SpanExporter
    private lateinit var conditionalSpanExporter: ConditionalSpanExporter

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
            conditionalSpanExporter = ConditionalSpanExporter(
                delegate = mockDelegate,
                allowNormalSpans = true,
                allowErrorSpans = true
            )
        }

        @Test
        fun `should export both normal and error spans when both are allowed`() {
            val normalSpan = createMockSpan("normal.span")
            val errorSpan = createMockSpan(InstrumentationManager.ERROR_SPAN_NAME)
            val spans = listOf(normalSpan, errorSpan)

            every { mockDelegate.export(spans) } returns CompletableResultCode.ofSuccess()

            val result = conditionalSpanExporter.export(spans)

            assertTrue(result.isSuccess)
            verify(exactly = 1) { mockDelegate.export(spans) }
        }
    }

    @Nested
    @DisplayName("Export Tests - Allow Normal Only")
    inner class ExportTestsAllowNormalOnly {

        @BeforeEach
        fun setup() {
            conditionalSpanExporter = ConditionalSpanExporter(
                delegate = mockDelegate,
                allowNormalSpans = true,
                allowErrorSpans = false
            )
        }

        @Test
        fun `should export only normal spans when error spans are not allowed`() {
            val normalSpan = createMockSpan("normal.span")
            val errorSpan = createMockSpan(InstrumentationManager.ERROR_SPAN_NAME)

            every { mockDelegate.export(listOf(normalSpan)) } returns CompletableResultCode.ofSuccess()

            val result = conditionalSpanExporter.export(listOf(normalSpan, errorSpan))

            assertTrue(result.isSuccess)
            verify(exactly = 1) { mockDelegate.export(listOf(normalSpan)) }
        }

        @Test
        fun `should return success without calling delegate when only error spans provided`() {
            val errorSpan1 = createMockSpan(InstrumentationManager.ERROR_SPAN_NAME)
            val errorSpan2 = createMockSpan(InstrumentationManager.ERROR_SPAN_NAME)

            val result = conditionalSpanExporter.export(listOf(errorSpan1, errorSpan2))

            assertTrue(result.isSuccess)
            verify(exactly = 0) { mockDelegate.export(any()) }
        }
    }

    @Nested
    @DisplayName("Export Tests - Allow Error Only")
    inner class ExportTestsAllowErrorOnly {

        @BeforeEach
        fun setup() {
            conditionalSpanExporter = ConditionalSpanExporter(
                delegate = mockDelegate,
                allowNormalSpans = false,
                allowErrorSpans = true
            )
        }

        @Test
        fun `should export only error spans when normal spans are not allowed`() {
            val normalSpan = createMockSpan("normal.span")
            val errorSpan = createMockSpan(InstrumentationManager.ERROR_SPAN_NAME)

            every { mockDelegate.export(listOf(errorSpan)) } returns CompletableResultCode.ofSuccess()

            val result = conditionalSpanExporter.export(listOf(normalSpan, errorSpan))

            assertTrue(result.isSuccess)
            verify(exactly = 1) { mockDelegate.export(listOf(errorSpan)) }
        }

        @Test
        fun `should return success without calling delegate when only normal spans provided`() {
            val normalSpan1 = createMockSpan("span1")
            val normalSpan2 = createMockSpan("span2")

            val result = conditionalSpanExporter.export(listOf(normalSpan1, normalSpan2))

            assertTrue(result.isSuccess)
            verify(exactly = 0) { mockDelegate.export(any()) }
        }
    }

    @Nested
    @DisplayName("Export Tests - Allow None")
    inner class ExportTestsAllowNone {

        @BeforeEach
        fun setup() {
            conditionalSpanExporter = ConditionalSpanExporter(
                delegate = mockDelegate,
                allowNormalSpans = false,
                allowErrorSpans = false
            )
        }

        @Test
        fun `should return success without calling delegate when no spans are allowed`() {
            val normalSpan = createMockSpan("normal.span")
            val errorSpan = createMockSpan(InstrumentationManager.ERROR_SPAN_NAME)

            val result = conditionalSpanExporter.export(listOf(normalSpan, errorSpan))

            assertTrue(result.isSuccess)
            verify(exactly = 0) { mockDelegate.export(any()) }
        }
    }

    @Nested
    @DisplayName("Export Tests - Edge Cases")
    inner class ExportTestsEdgeCases {

        @BeforeEach
        fun setup() {
            conditionalSpanExporter = ConditionalSpanExporter(
                delegate = mockDelegate,
                allowNormalSpans = true,
                allowErrorSpans = true
            )
        }

        @Test
        fun `should return success without calling delegate when empty collection provided`() {
            val emptySpans = emptyList<SpanData>()

            val result = conditionalSpanExporter.export(emptySpans)

            assertTrue(result.isSuccess)
            verify(exactly = 0) { mockDelegate.export(any()) }
        }

        @Test
        fun `should propagate delegate export failure`() {
            val spans = listOf(createMockSpan("normal.span"))

            every { mockDelegate.export(spans) } returns CompletableResultCode.ofFailure()

            val result = conditionalSpanExporter.export(spans)

            assertFalse(result.isSuccess)
            verify(exactly = 1) { mockDelegate.export(spans) }
        }
    }

    @Nested
    @DisplayName("Flush Tests")
    inner class FlushTests {

        @BeforeEach
        fun setup() {
            conditionalSpanExporter = ConditionalSpanExporter(
                delegate = mockDelegate,
                allowNormalSpans = true,
                allowErrorSpans = true
            )
        }

        @Test
        fun `should delegate flush to underlying exporter and propagate result`() {
            every { mockDelegate.flush() } returns CompletableResultCode.ofSuccess()

            val result = conditionalSpanExporter.flush()

            assertTrue(result.isSuccess)
            verify(exactly = 1) { mockDelegate.flush() }
        }
    }

    @Nested
    @DisplayName("Shutdown Tests")
    inner class ShutdownTests {

        @BeforeEach
        fun setup() {
            conditionalSpanExporter = ConditionalSpanExporter(
                delegate = mockDelegate,
                allowNormalSpans = true,
                allowErrorSpans = true
            )
        }

        @Test
        fun `should delegate shutdown to underlying exporter and propagate result`() {
            every { mockDelegate.shutdown() } returns CompletableResultCode.ofSuccess()

            val result = conditionalSpanExporter.shutdown()

            assertTrue(result.isSuccess)
            verify(exactly = 1) { mockDelegate.shutdown() }
        }
    }

    private fun createMockSpan(name: String): SpanData {
        return mockk<SpanData>().apply {
            every { getName() } returns name
        }
    }
}
