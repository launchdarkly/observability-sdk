package com.launchdarkly.LDNative

import com.launchdarkly.observability.bridge.KotlinSpanBuilder

/**
 * Bindable wrapper around [KotlinSpanBuilder] for C# via Xamarin binding.
 */
class RealSpanBuilder internal constructor(
    private val delegate: KotlinSpanBuilder
) {
    val traceId: String get() = delegate.traceId
    val spanId: String get() = delegate.spanId

    fun setAttribute(key: String, value: Any?) {
        delegate.setAttribute(key, value)
    }

    fun setAttributes(attributes: HashMap<String, Any?>) {
        delegate.setAttributes(attributes)
    }

    fun addEvent(name: String) {
        delegate.addEvent(name)
    }

    fun addEventWithAttributes(name: String, attributes: HashMap<String, Any?>) {
        delegate.addEvent(name, attributes)
    }

    fun recordException(message: String, type: String) {
        delegate.recordException(message, type)
    }

    fun recordExceptionWithAttributes(message: String, type: String, attributes: HashMap<String, Any?>) {
        delegate.recordException(message, type, attributes)
    }

    fun setStatus(code: Int) {
        delegate.setStatus(code)
    }

    fun end(epochSeconds: Double) {
        delegate.end(epochSeconds)
    }
}
