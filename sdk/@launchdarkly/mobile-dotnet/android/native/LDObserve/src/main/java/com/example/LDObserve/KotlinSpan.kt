package com.launchdarkly.LDNative

import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanContext
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.api.trace.TraceFlags
import io.opentelemetry.api.trace.TraceState
import java.util.concurrent.TimeUnit

/**
 * Plain data holder constructed by C# from `System.Diagnostics.Activity` properties.
 * Does NOT implement [Span] so the Xamarin binding generator can bind it.
 * Use [toSpan] to obtain a real [Span] on the Kotlin side.
 */
class SpanData(
    val traceIdHex: String,
    val spanIdHex: String,
    val spanName: String,
    val statusCodeInt: Int,
    val spanAttributes: HashMap<String, Any?>?
)

/**
 * Converts [SpanData] into an OpenTelemetry [Span].
 */
fun SpanData.toSpan(): Span = KotlinSpan(this)

private data class SpanEvent(
    val name: String,
    val attributes: Attributes,
    val timestampNanos: Long
)

/**
 * Internal [Span] implementation backed by [SpanData].
 */
internal class KotlinSpan(data: SpanData) : Span {

    private val _context: SpanContext = SpanContext.create(
        data.traceIdHex,
        data.spanIdHex,
        TraceFlags.getSampled(),
        TraceState.getDefault()
    )

    private var _name: String = data.spanName

    private var _statusCode: StatusCode = when (data.statusCodeInt) {
        1 -> StatusCode.OK
        2 -> StatusCode.ERROR
        else -> StatusCode.UNSET
    }
    private var _statusDescription: String = ""
    private var _isRecording: Boolean = true
    private val _events: MutableList<SpanEvent> = mutableListOf()

    private val _attributes: MutableMap<String, Any> = run {
        val map = mutableMapOf<String, Any>()
        data.spanAttributes?.forEach { (key, value) ->
            if (value != null) map[key] = value
        }
        map
    }

    override fun <T : Any> setAttribute(key: AttributeKey<T>, value: T?): Span {
        if (value != null) {
            _attributes[key.key] = value
        }
        return this
    }

    override fun addEvent(name: String): Span {
        _events.add(SpanEvent(name, Attributes.empty(), System.nanoTime()))
        return this
    }

    override fun addEvent(name: String, attributes: Attributes): Span {
        _events.add(SpanEvent(name, attributes, System.nanoTime()))
        return this
    }

    override fun addEvent(name: String, timestamp: Long, unit: TimeUnit): Span {
        _events.add(SpanEvent(name, Attributes.empty(), unit.toNanos(timestamp)))
        return this
    }

    override fun addEvent(name: String, attributes: Attributes, timestamp: Long, unit: TimeUnit): Span {
        _events.add(SpanEvent(name, attributes, unit.toNanos(timestamp)))
        return this
    }

    override fun setStatus(statusCode: StatusCode): Span {
        _statusCode = statusCode
        return this
    }

    override fun setStatus(statusCode: StatusCode, description: String): Span {
        _statusCode = statusCode
        _statusDescription = description
        return this
    }

    override fun recordException(exception: Throwable): Span {
        val attrs = Attributes.of(
            AttributeKey.stringKey("exception.type"), exception.javaClass.name,
            AttributeKey.stringKey("exception.message"), exception.message ?: ""
        )
        _events.add(SpanEvent("exception", attrs, System.nanoTime()))
        return this
    }

    override fun recordException(exception: Throwable, additionalAttributes: Attributes): Span {
        val attrs = Attributes.builder()
            .put(AttributeKey.stringKey("exception.type"), exception.javaClass.name)
            .put(AttributeKey.stringKey("exception.message"), exception.message ?: "")
            .putAll(additionalAttributes)
            .build()
        _events.add(SpanEvent("exception", attrs, System.nanoTime()))
        return this
    }

    override fun updateName(name: String): Span {
        _name = name
        return this
    }

    override fun end() {
        _isRecording = false
    }

    override fun end(timestamp: Long, unit: TimeUnit) {
        _isRecording = false
    }

    override fun getSpanContext(): SpanContext = _context

    override fun isRecording(): Boolean = _isRecording
}
