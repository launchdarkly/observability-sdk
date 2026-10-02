package com.launchdarkly.observability.bridge

import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.logs.Logger
import io.opentelemetry.api.logs.Severity
import io.opentelemetry.api.trace.SpanContext
import io.opentelemetry.context.Context
import java.util.concurrent.TimeUnit

/**
 * Emits a log record through the OTel [Logger], setting span context when provided.
 * Shared by [KotlinLogger] and ObservabilityService to avoid duplication.
 */
internal fun Logger.emitLog(
    message: String,
    severity: Severity,
    attributes: Attributes,
    spanContext: SpanContext?
) {
    val builder = logRecordBuilder()
        .setBody(message)
        .setTimestamp(System.currentTimeMillis(), TimeUnit.MILLISECONDS)
        .setSeverity(severity)
        .setSeverityText(severity.toString())
        .setAllAttributes(attributes)

    if (spanContext != null) {
        builder.setContext(Context.root().with(
            io.opentelemetry.api.trace.Span.wrap(spanContext)
        ))
    }

    builder.emit()
}
