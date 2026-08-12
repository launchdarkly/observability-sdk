package com.launchdarkly.observability.client

import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.context.Context
import io.opentelemetry.sdk.logs.LogRecordProcessor
import io.opentelemetry.sdk.logs.ReadWriteLogRecord
import io.opentelemetry.sdk.trace.ReadWriteSpan
import io.opentelemetry.sdk.trace.ReadableSpan
import io.opentelemetry.sdk.trace.SpanProcessor

private val SESSION_ID = AttributeKey.stringKey(ObservabilityService.SESSION_ID_ATTRIBUTE)

/**
 * Stamps `session.id` onto every span at start time.
 *
 * The full product gets equivalent appenders from OpenTelemetry Android's RUM pipeline; these exist
 * so the OTel-only product produces identically stamped signals without that dependency. Metrics
 * are stamped separately at record time, because instruments carry attributes per measurement
 * rather than through a processor.
 */
internal class SessionIdSpanAppender(
    private val sessionProvider: LDSessionManaging,
) : SpanProcessor {
    override fun onStart(parentContext: Context, span: ReadWriteSpan) {
        span.setAttribute(SESSION_ID, sessionProvider.getSessionId())
    }

    override fun isStartRequired(): Boolean = true

    override fun onEnd(span: ReadableSpan) {}

    override fun isEndRequired(): Boolean = false
}

/** Stamps `session.id` onto every log record as it is emitted. */
internal class SessionIdLogRecordAppender(
    private val sessionProvider: LDSessionManaging,
) : LogRecordProcessor {
    override fun onEmit(context: Context, logRecord: ReadWriteLogRecord) {
        logRecord.setAttribute(SESSION_ID, sessionProvider.getSessionId())
    }
}
