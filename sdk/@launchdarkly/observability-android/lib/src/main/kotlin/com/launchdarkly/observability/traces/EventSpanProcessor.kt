package com.launchdarkly.observability.traces

import com.launchdarkly.observability.replay.transport.BatchWorker
import com.launchdarkly.observability.replay.transport.EventQueue
import com.launchdarkly.observability.sampling.ExportSampler
import com.launchdarkly.observability.sampling.sampleSpans
import io.opentelemetry.context.Context
import io.opentelemetry.sdk.common.CompletableResultCode
import io.opentelemetry.sdk.trace.ReadWriteSpan
import io.opentelemetry.sdk.trace.ReadableSpan
import io.opentelemetry.sdk.trace.SpanProcessor

/**
 * A [SpanProcessor] that forwards sampled spans as [SpanItemPayload]s into a shared [EventQueue].
 *
 * Replaces the OpenTelemetry `BatchSpanProcessor` + `OtlpHttpSpanExporter` wiring previously
 * used for traces. Mirrors the Swift `EventSpanProcessor`.
 *
 * Applies the same pre-export rewrites that `SamplingTraceExporter` did:
 *   - [applyBridgeIdOverrides] to honour bridge-supplied trace/span IDs,
 *   - [sampleSpans] to drop spans whose sampling configuration rejects them.
 */
internal class EventSpanProcessor(
    private val eventQueue: EventQueue,
    private val sampler: ExportSampler,
    private val batchWorker: BatchWorker? = null,
) : SpanProcessor {

    override fun onStart(parentContext: Context, span: ReadWriteSpan) {
        // No-op: this processor only reacts to end events.
    }

    override fun isStartRequired(): Boolean = false

    override fun onEnd(span: ReadableSpan) {
        if (!span.spanContext.isSampled) return

        val data = applyBridgeIdOverrides(span.toSpanData())
        val sampled = sampleSpans(listOf(data), sampler)
        if (sampled.isEmpty()) return

        eventQueue.send(sampled.map { SpanItemPayload(it) })
    }

    override fun isEndRequired(): Boolean = true

    override fun forceFlush(): CompletableResultCode {
        batchWorker?.flush()
        return CompletableResultCode.ofSuccess()
    }

    override fun shutdown(): CompletableResultCode {
        batchWorker?.flush()
        return CompletableResultCode.ofSuccess()
    }
}
