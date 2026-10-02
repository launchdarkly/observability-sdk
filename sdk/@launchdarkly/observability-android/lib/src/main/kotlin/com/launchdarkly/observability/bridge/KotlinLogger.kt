package com.launchdarkly.observability.bridge

import com.launchdarkly.observability.interfaces.LogsApi
import io.opentelemetry.api.logs.Logger
import io.opentelemetry.api.logs.Severity
import io.opentelemetry.api.trace.SpanContext
import io.opentelemetry.api.trace.TraceFlags
import io.opentelemetry.api.trace.TraceState

/**
 * Wraps the OTel [Logger] for bridge layers (e.g. .NET MAUI).
 * Mirrors [KotlinTracer] but for log records.
 *
 * Holds two loggers:
 * - [internalLogger]: raw OTel Logger, bypasses level-gating, supports span context.
 * - [customerLogger]: level-gated [LogsApi] delegate for customer-facing logs.
 */
class KotlinLogger internal constructor(
    private val internalLogger: Logger,
    private val customerLogger: LogsApi
) {

    fun recordLog(message: String, severityNumber: Int,
                  traceId: String?, spanId: String?,
                  isInternal: Boolean,
                  attributes: Map<String, Any?>?) {
        val severity = Severity.values().firstOrNull { it.severityNumber == severityNumber }
            ?: Severity.INFO
        val spanContext = if (!traceId.isNullOrEmpty() && !spanId.isNullOrEmpty()) {
            SpanContext.create(traceId, spanId, TraceFlags.getSampled(), TraceState.getDefault())
        } else null
        val attrs = AttributeConverter.convert(attributes)

        if (isInternal) {
            internalLogger.emitLog(message, severity, attrs, spanContext)
        } else {
            customerLogger.recordLog(message, severity, attrs, spanContext)
        }
    }
}
