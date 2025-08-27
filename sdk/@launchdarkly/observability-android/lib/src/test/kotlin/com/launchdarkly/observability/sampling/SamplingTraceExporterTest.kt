package com.launchdarkly.observability.sampling

import com.launchdarkly.observability.sampling.utils.MockExportSampler
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import io.opentelemetry.api.trace.SpanContext
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter
import io.opentelemetry.sdk.common.CompletableResultCode
import io.opentelemetry.sdk.trace.data.SpanData
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested

class SamplingTraceExporterTest {

    private lateinit var mockDelegate: OtlpHttpSpanExporter
    private lateinit var mockSampler: ExportSampler
    private lateinit var samplingTraceExporter: SamplingTraceExporter

    @BeforeEach
    fun setup() {
        mockDelegate = mockk()
        mockSampler = MockExportSampler()
        samplingTraceExporter = SamplingTraceExporter(mockDelegate, mockSampler)
    }

    @Nested
    @DisplayName("Export Tests")
    inner class ExportTests {

        @Test
        fun `should return success when no spans are left to export after sampling`() {
            val spans = listOf(
                createMockSpan("span1", "span1-id"),
                createMockSpan("span2", "span2-id"),
                createMockSpan("span3", "span3-id")
            )

            mockkStatic("com.launchdarkly.observability.sampling.SampleSpansKt")
            every { sampleSpans(spans, mockSampler) } returns emptyList()

            val expectedResult = CompletableResultCode.ofSuccess()

            val result = samplingTraceExporter.export(spans)

            assertEquals(expectedResult.isDone, result.isDone)
            assertTrue(result.isSuccess)

            // Verify delegate export was never called
            verify(exactly = 0) { mockDelegate.export(any()) }

            unmockkStatic("com.launchdarkly.observability.sampling.SampleSpansKt")
        }

        @Test
        fun `should delegate to underlying exporter when spans are sampled`() {
            val originalSpans = listOf(
                createMockSpan("span1", "span1-id"),
                createMockSpan("span2", "span2-id"),
                createMockSpan("span3", "span3-id")
            )

            val sampledSpans = listOf(
                createMockSpan("span1", "span1-id"),
                createMockSpan("span3", "span3-id")
            )

            mockkStatic("com.launchdarkly.observability.sampling.SampleSpansKt")
            every { sampleSpans(originalSpans, mockSampler) } returns sampledSpans

            val expectedResult = CompletableResultCode.ofSuccess()
            every { mockDelegate.export(sampledSpans) } returns expectedResult

            val result = samplingTraceExporter.export(originalSpans)

            assertEquals(expectedResult, result)

            // Verify delegate export was called with sampled spans
            verify(exactly = 1) { mockDelegate.export(sampledSpans) }

            unmockkStatic("com.launchdarkly.observability.sampling.SampleSpansKt")
        }

        @Test
        fun `should handle empty collection`() {
            val emptySpans = emptyList<SpanData>()

            mockkStatic("com.launchdarkly.observability.sampling.SampleSpansKt")
            every { sampleSpans(emptySpans, mockSampler) } returns emptyList()

            val result = samplingTraceExporter.export(emptySpans)

            assertTrue(result.isSuccess)
            verify(exactly = 0) { mockDelegate.export(any()) }

            unmockkStatic("com.launchdarkly.observability.sampling.SampleSpansKt")
        }

        @Test
        fun `should propagate delegate export failure`() {
            val originalSpans = listOf(createMockSpan("span1", "span1-id"))
            val sampledSpans = listOf(createMockSpan("span1", "span1-id"))

            mockkStatic("com.launchdarkly.observability.sampling.SampleSpansKt")
            every { sampleSpans(originalSpans, mockSampler) } returns sampledSpans

            val failedResult = CompletableResultCode.ofFailure()
            every { mockDelegate.export(sampledSpans) } returns failedResult

            val result = samplingTraceExporter.export(originalSpans)

            assertEquals(failedResult, result)
            assertFalse(result.isSuccess)

            unmockkStatic("com.launchdarkly.observability.sampling.SampleSpansKt")
        }
    }

    @Nested
    @DisplayName("Flush Tests")
    inner class FlushTests {

        @Test
        fun `should delegate flush to underlying exporter and propagate result`() {
            val expectedResult = CompletableResultCode.ofSuccess()
            every { mockDelegate.flush() } returns expectedResult

            val result = samplingTraceExporter.flush()

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

            val result = samplingTraceExporter.shutdown()

            assertEquals(expectedResult, result)
            verify(exactly = 1) { mockDelegate.shutdown() }
        }
    }

    private fun createMockSpan(
        name: String,
        spanId: String,
        parentSpanId: String? = null
    ): SpanData {
        val spanContext = mockk<SpanContext>().apply {
            every { getSpanId() } returns spanId
        }

        val parentSpanContext = if (parentSpanId != null) {
            mockk<SpanContext>().apply {
                every { getSpanId() } returns parentSpanId
            }
        } else null

        return mockk<SpanData>().apply {
            every { getName() } returns name
            every { getSpanContext() } returns spanContext
            every { getParentSpanContext() } returns parentSpanContext
        }
    }
}
