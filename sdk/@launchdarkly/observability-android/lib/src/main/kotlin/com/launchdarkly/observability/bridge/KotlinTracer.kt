package com.launchdarkly.observability.bridge

import io.opentelemetry.api.trace.SpanContext
import io.opentelemetry.api.trace.TraceFlags
import io.opentelemetry.api.trace.TraceState
import io.opentelemetry.api.trace.Tracer
import java.time.Instant

/** Attribute keys used to carry bridge-supplied IDs through the
 *  OTel pipeline so the exporter can override the auto-generated IDs. */
const val BRIDGE_TRACE_ID_ATTRIBUTE_KEY = "__bridge.trace_id"
const val BRIDGE_SPAN_ID_ATTRIBUTE_KEY = "__bridge.span_id"

/**
 * Wraps the OTel [Tracer] for bridge layers (e.g. .NET MAUI).
 * Returns [KotlinSpanBuilder] instances that hold a live span.
 */
class KotlinTracer internal constructor(private val tracer: Tracer) {

    fun spanBuilder(name: String, startTimeEpochSeconds: Double,
                    traceId: String, spanId: String, parentSpanId: String): KotlinSpanBuilder {

        val builder = tracer.spanBuilder(name)
        val startInstant = Instant.ofEpochSecond(
            startTimeEpochSeconds.toLong(),
            ((startTimeEpochSeconds % 1) * 1_000_000_000).toLong()
        )
        builder.setStartTimestamp(startInstant)

        if (parentSpanId.isNotEmpty() && parentSpanId != "0000000000000000") {
            val parentContext = SpanContext.createFromRemoteParent(
                traceId,
                parentSpanId,
                TraceFlags.getSampled(),
                TraceState.getDefault()
            )
            builder.setParent(io.opentelemetry.context.Context.root().with(
                io.opentelemetry.api.trace.Span.wrap(parentContext)
            ))
        }

        if (traceId.isNotEmpty()) {
            builder.setAttribute(BRIDGE_TRACE_ID_ATTRIBUTE_KEY, traceId)
        }
        if (spanId.isNotEmpty()) {
            builder.setAttribute(BRIDGE_SPAN_ID_ATTRIBUTE_KEY, spanId)
        }

        val span = builder.startSpan()
        return KotlinSpanBuilder(span)
    }
}
