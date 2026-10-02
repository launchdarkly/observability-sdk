package com.launchdarkly.LDNative

import com.launchdarkly.observability.bridge.KotlinLogger

/**
 * Bindable wrapper around [KotlinLogger] for C# via Xamarin binding.
 */
class RealLogger internal constructor(
    private val delegate: KotlinLogger
) {
    fun recordLog(message: String, severityNumber: Int,
                  traceId: String?, spanId: String?,
                  isInternal: Boolean,
                  attributes: HashMap<String, Any?>?) {
        delegate.recordLog(message, severityNumber, traceId, spanId, isInternal, attributes)
    }
}
