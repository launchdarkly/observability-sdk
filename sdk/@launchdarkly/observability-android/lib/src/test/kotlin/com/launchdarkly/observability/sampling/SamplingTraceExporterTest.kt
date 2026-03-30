package com.launchdarkly.observability.sampling

import com.launchdarkly.observability.sampling.utils.FakeExportSampler
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import io.opentelemetry.api.trace.SpanContext
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.api.trace.TraceFlags
import io.opentelemetry.api.trace.TraceState
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter
import io.opentelemetry.sdk.common.CompletableResultCode
import io.opentelemetry.sdk.testing.trace.TestSpanData
import io.opentelemetry.sdk.trace.data.SpanData
import io.opentelemetry.sdk.trace.data.StatusData
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested

class SamplingTraceExporterTest {

    private lateinit var mockDelegate: OtlpHttpSpanExporter
    private lateinit var sampler: ExportSampler
    private lateinit var samplingTraceExporter: SamplingTraceExporter

    @BeforeEach
    fun setup() {
        mockDelegate = mockk()
        sampler = FakeExportSampler()
        samplingTraceExporter = SamplingTraceExporter(mockDelegate, sampler)
    }

    @Nested
    @DisplayName("Export Tests")
    inner class ExportTests {

        @Test
        fun `should return success when no spans are left to export after sampling`() {
            val spans = listOf(
                createSpanData("span1", SPAN_ID_1),
                createSpanData("span2", SPAN_ID_2),
                createSpanData("span3", SPAN_ID_3)
            )

            mockkStatic("com.launchdarkly.observability.sampling.SampleSpansKt")
            every { sampleSpans(spans, sampler) } returns emptyList()

            val expectedResult = CompletableResultCode.ofSuccess()

            val result = samplingTraceExporter.export(spans)

            assertEquals(expectedResult.isDone, result.isDone)
            assertTrue(result.isSuccess)

            verify(exactly = 0) { mockDelegate.export(any()) }

            unmockkStatic("com.launchdarkly.observability.sampling.SampleSpansKt")
        }

        @Test
        fun `should delegate to underlying exporter when spans are sampled`() {
            val originalSpans = listOf(
                createSpanData("span1", SPAN_ID_1),
                createSpanData("span2", SPAN_ID_2),
                createSpanData("span3", SPAN_ID_3)
            )

            val sampledSpans = listOf(
                createSpanData("span1", SPAN_ID_1),
                createSpanData("span3", SPAN_ID_3)
            )

            mockkStatic("com.launchdarkly.observability.sampling.SampleSpansKt")
            every { sampleSpans(originalSpans, sampler) } returns sampledSpans

            val expectedResult = CompletableResultCode.ofSuccess()
            every { mockDelegate.export(sampledSpans) } returns expectedResult

            val result = samplingTraceExporter.export(originalSpans)

            assertEquals(expectedResult, result)

            verify(exactly = 1) { mockDelegate.export(sampledSpans) }

            unmockkStatic("com.launchdarkly.observability.sampling.SampleSpansKt")
        }

        @Test
        fun `should handle empty collection`() {
            val emptySpans = emptyList<SpanData>()

            mockkStatic("com.launchdarkly.observability.sampling.SampleSpansKt")
            every { sampleSpans(emptySpans, sampler) } returns emptyList()

            val result = samplingTraceExporter.export(emptySpans)

            assertTrue(result.isSuccess)
            verify(exactly = 0) { mockDelegate.export(any()) }

            unmockkStatic("com.launchdarkly.observability.sampling.SampleSpansKt")
        }

        @Test
        fun `should propagate delegate export failure`() {
            val originalSpans = listOf(createSpanData("span1", SPAN_ID_1))
            val sampledSpans = listOf(createSpanData("span1", SPAN_ID_1))

            mockkStatic("com.launchdarkly.observability.sampling.SampleSpansKt")
            every { sampleSpans(originalSpans, sampler) } returns sampledSpans

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

    companion object {
        private const val TRACE_ID = "00000000000000000000000000000001"
        private const val SPAN_ID_1 = "a000000000000001"
        private const val SPAN_ID_2 = "a000000000000002"
        private const val SPAN_ID_3 = "a000000000000003"
    }

    private fun createSpanData(
        name: String,
        spanId: String,
        parentSpanId: String? = null
    ): SpanData {
        val spanContext = SpanContext.create(
            TRACE_ID, spanId, TraceFlags.getSampled(), TraceState.getDefault()
        )
        val parentSpanContext = if (parentSpanId != null) {
            SpanContext.create(
                TRACE_ID, parentSpanId, TraceFlags.getSampled(), TraceState.getDefault()
            )
        } else {
            SpanContext.getInvalid()
        }

        return TestSpanData.builder()
            .setName(name)
            .setKind(SpanKind.INTERNAL)
            .setSpanContext(spanContext)
            .setParentSpanContext(parentSpanContext)
            .setStatus(StatusData.ok())
            .setStartEpochNanos(0)
            .setEndEpochNanos(1)
            .setHasEnded(true)
            .build()
    }
}
