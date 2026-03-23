package com.launchdarkly.observability.bridge

import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.StatusCode
import java.util.concurrent.TimeUnit

/**
 * Wraps a live OTel [Span] with simple-typed methods that the
 * native AAR adapter can expose to C# via Xamarin binding.
 */
class KotlinSpanBuilder internal constructor(private val span: Span) {

    val traceId: String get() = span.spanContext.traceId
    val spanId: String get() = span.spanContext.spanId
    val spanKind: Int get() = 0 // not readable from Span interface after creation

    fun setAttribute(key: String, value: Any?) {
        when (value) {
            is String  -> span.setAttribute(key, value)
            is Boolean -> span.setAttribute(key, value)
            is Long    -> span.setAttribute(key, value)
            is Int     -> span.setAttribute(key, value.toLong())
            is Double  -> span.setAttribute(key, value)
            is Float   -> span.setAttribute(key, value.toDouble())
            null       -> {}
            else       -> span.setAttribute(key, value.toString())
        }
    }

    fun setAttributes(attributes: Map<String, Any?>) {
        val attrs = AttributeConverter.convert(attributes)
        attrs.forEach { key, value ->
            @Suppress("UNCHECKED_CAST")
            span.setAttribute(key as AttributeKey<Any>, value)
        }
    }

    fun addEvent(name: String) {
        span.addEvent(name)
    }

    fun addEvent(name: String, attributes: Map<String, Any?>) {
        span.addEvent(name, AttributeConverter.convert(attributes))
    }

    fun recordException(message: String, type: String) {
        span.recordException(RuntimeException("$type: $message"))
    }

    fun recordException(message: String, type: String, attributes: Map<String, Any?>) {
        span.recordException(
            RuntimeException("$type: $message"),
            AttributeConverter.convert(attributes)
        )
    }

    fun setStatus(code: Int) {
        when (code) {
            1 -> span.setStatus(StatusCode.OK)
            2 -> span.setStatus(StatusCode.ERROR)
        }
    }

    fun end(epochSeconds: Double) {
        val nanos = (epochSeconds * 1_000_000_000).toLong()
        span.end(nanos, TimeUnit.NANOSECONDS)
    }
}
