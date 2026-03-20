package com.launchdarkly.LDNative

import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanContext
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.api.trace.TraceFlags
import io.opentelemetry.api.trace.TraceState
import java.util.concurrent.TimeUnit

private data class SpanEvent(
    val name: String,
    val attributes: Attributes,
    val timestampNanos: Long
)

/**
 * Representation of a `System.Diagnostics.Activity` that implements the
 * OpenTelemetry [Span] interface.
 *
 * The constructor accepts only JVM primitives / collections so the C#
 * Xamarin binding can construct instances from `Activity` properties.
 *
 * @param traceIdHex   32-char hex trace identifier.
 * @param spanIdHex    16-char hex span identifier.
 * @param spanName     Display name / operation name.
 * @param statusCodeInt 0 = UNSET, 1 = OK, 2 = ERROR.
 * @param spanAttributes String-keyed attribute map (nullable).
 */
class KotlinSpan(
    traceIdHex: String,
    spanIdHex: String,
    private var spanName: String,
    statusCodeInt: Int,
    spanAttributes: HashMap<String, Any?>?
) : Span {

    private val _context: SpanContext = SpanContext.create(
        traceIdHex,
        spanIdHex,
        TraceFlags.getSampled(),
        TraceState.getDefault()
    )

    private var _statusCode: StatusCode = when (statusCodeInt) {
        1 -> StatusCode.OK
        2 -> StatusCode.ERROR
        else -> StatusCode.UNSET
    }
    private var _statusDescription: String = ""
    private var _isRecording: Boolean = true
    private val _events: MutableList<SpanEvent> = mutableListOf()

    private val _attributesBuilder: Attributes.AttributesBuilder = run {
        val builder = Attributes.builder()
        spanAttributes?.forEach { (key, value) ->
            when (value) {
                is String  -> builder.put(AttributeKey.stringKey(key), value)
                is Boolean -> builder.put(AttributeKey.booleanKey(key), value)
                is Long    -> builder.put(AttributeKey.longKey(key), value)
                is Int     -> builder.put(AttributeKey.longKey(key), value.toLong())
                is Double  -> builder.put(AttributeKey.doubleKey(key), value)
                is Float   -> builder.put(AttributeKey.doubleKey(key), value.toDouble())
            }
        }
        builder
    }

    override fun <T : Any?> setAttribute(key: AttributeKey<T>, value: T): Span {
        _attributesBuilder.put(key, value)
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
        spanName = name
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
