package com.launchdarkly.LDNative

import com.launchdarkly.observability.bridge.KotlinTracer

/**
 * Bindable wrapper around [KotlinTracer] for C# via Xamarin binding.
 */
class RealTracer internal constructor(
    private val delegate: KotlinTracer
) {
    fun spanBuilder(name: String, startTimeEpochSeconds: Double,
                    traceId: String, parentSpanId: String): RealSpanBuilder {
        val builder = delegate.spanBuilder(name, startTimeEpochSeconds, traceId, parentSpanId)
        return RealSpanBuilder(builder)
    }
}
