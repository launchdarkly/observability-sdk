package com.launchdarkly.observability.traces

import com.launchdarkly.observability.bridge.BRIDGE_SPAN_ID_ATTRIBUTE_KEY
import com.launchdarkly.observability.bridge.BRIDGE_TRACE_ID_ATTRIBUTE_KEY
import com.launchdarkly.observability.replay.transport.EventQueue
import com.launchdarkly.observability.sampling.SamplingResult
import com.launchdarkly.observability.sampling.utils.FakeExportSampler
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.SpanContext
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.api.trace.TraceFlags
import io.opentelemetry.api.trace.TraceState
import io.opentelemetry.sdk.testing.trace.TestSpanData
import io.opentelemetry.sdk.trace.ReadableSpan
import io.opentelemetry.sdk.trace.data.SpanData
import io.opentelemetry.sdk.trace.data.StatusData
import io.opentelemetry.sdk.trace.export.SpanExporter
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class EventSpanProcessorTest {

    private val sampledCtx = SpanContext.create(
        "0102030405060708090a0b0c0d0e0f10",
        "1112131415161718",
        TraceFlags.getSampled(),
        TraceState.getDefault(),
    )

    private val unsampledCtx = SpanContext.create(
        "0102030405060708090a0b0c0d0e0f10",
        "1112131415161718",
        TraceFlags.getDefault(),
        TraceState.getDefault(),
    )

    @Test
    fun `onEnd drops spans that are not sampled by the recording context`() {
        val queue = EventQueue()
        val processor = EventSpanProcessor(queue, sampler = FakeExportSampler())

        val span = spanMock(
            spanData(unsampledCtx, Attributes.empty()),
            isSampled = false,
        )
        processor.onEnd(span)

        assertNull(
            queue.earliest(costBudget = Int.MAX_VALUE, limit = 10, except = emptySet()),
            "queue should remain empty",
        )
    }

    @Test
    fun `onEnd drops spans rejected by the export sampler`() {
        val queue = EventQueue()
        val sampler = FakeExportSampler(sampleSpan = { SamplingResult(sample = false) })
        val processor = EventSpanProcessor(queue, sampler)

        val span = spanMock(spanData(sampledCtx, Attributes.empty()))
        processor.onEnd(span)

        assertNull(queue.earliest(costBudget = Int.MAX_VALUE, limit = 10, except = emptySet()))
    }

    @Test
    fun `onEnd enqueues a SpanItemPayload for sampled spans`() {
        val queue = EventQueue()
        val processor = EventSpanProcessor(queue, sampler = FakeExportSampler())

        val data = spanData(sampledCtx, Attributes.empty())
        processor.onEnd(spanMock(data))

        val batch = queue.earliest(costBudget = Int.MAX_VALUE, limit = 10, except = emptySet())
        assertTrue(batch != null)
        assertEquals(OtlpTraceExporter::class.java, batch!!.exporterClass)
        val payload = batch.items[0].payload as SpanItemPayload
        assertEquals(data.name, payload.spanData.name)
    }

    @Test
    fun `onEnd exports sampled spans to delegateExporter`() {
        val queue = EventQueue()
        val exporter = mockk<SpanExporter>(relaxed = true)
        val processor = EventSpanProcessor(queue, sampler = FakeExportSampler(), delegateExporter = exporter)

        val data = spanData(sampledCtx, Attributes.empty())
        processor.onEnd(spanMock(data))

        verify {
            exporter.export(match { it.size == 1 && it[0].name == data.name })
        }
    }

    @Test
    fun `onEnd applies bridge trace and span id overrides before enqueueing`() {
        val overrideTraceId = "aabbccddeeff00112233445566778899"
        val overrideSpanId = "fedcba9876543210"

        val queue = EventQueue()
        val processor = EventSpanProcessor(queue, sampler = FakeExportSampler())

        val data = spanData(
            sampledCtx,
            Attributes.builder()
                .put(AttributeKey.stringKey(BRIDGE_TRACE_ID_ATTRIBUTE_KEY), overrideTraceId)
                .put(AttributeKey.stringKey(BRIDGE_SPAN_ID_ATTRIBUTE_KEY), overrideSpanId)
                .put(AttributeKey.stringKey("user.id"), "abc")
                .build(),
        )
        processor.onEnd(spanMock(data))

        val batch = queue.earliest(costBudget = Int.MAX_VALUE, limit = 10, except = emptySet())
        val payload = batch!!.items[0].payload as SpanItemPayload
        assertEquals(overrideTraceId, payload.spanData.spanContext.traceId)
        assertEquals(overrideSpanId, payload.spanData.spanContext.spanId)
        assertNull(payload.spanData.attributes.get(AttributeKey.stringKey(BRIDGE_TRACE_ID_ATTRIBUTE_KEY)))
        assertNull(payload.spanData.attributes.get(AttributeKey.stringKey(BRIDGE_SPAN_ID_ATTRIBUTE_KEY)))
        assertEquals("abc", payload.spanData.attributes.get(AttributeKey.stringKey("user.id")))
    }

    private fun spanData(ctx: SpanContext, attributes: Attributes): SpanData =
        TestSpanData.builder()
            .setName("test-span")
            .setKind(SpanKind.INTERNAL)
            .setSpanContext(ctx)
            .setStartEpochNanos(1L)
            .setEndEpochNanos(2L)
            .setHasEnded(true)
            .setStatus(StatusData.unset())
            .setAttributes(attributes)
            .build()

    private fun spanMock(data: SpanData, isSampled: Boolean = true): ReadableSpan {
        val ctx = mockk<SpanContext>()
        every { ctx.isSampled } returns isSampled
        return mockk {
            every { spanContext } returns ctx
            every { toSpanData() } returns data
        }
    }
}
