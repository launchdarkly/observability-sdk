package com.launchdarkly.observability.bridge

import io.opentelemetry.api.trace.SpanContext
import io.opentelemetry.api.trace.TraceFlags
import io.opentelemetry.api.trace.TraceState
import io.opentelemetry.api.trace.Tracer
import java.time.Instant

/**
 * Wraps the OTel [Tracer] for bridge layers (e.g. .NET MAUI).
 * Returns [KotlinSpanBuilder] instances that hold a live span.
 */
class KotlinTracer internal constructor(private val tracer: Tracer) {

    fun spanBuilder(name: String, startTimeEpochSeconds: Double,
                    traceId: String, parentSpanId: String): KotlinSpanBuilder {

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

        val span = builder.startSpan()
        return KotlinSpanBuilder(span)
    }
}
