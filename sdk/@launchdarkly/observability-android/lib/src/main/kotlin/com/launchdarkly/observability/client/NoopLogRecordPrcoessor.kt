package com.launchdarkly.observability.client

import io.opentelemetry.context.Context
import io.opentelemetry.sdk.logs.LogRecordProcessor
import io.opentelemetry.sdk.logs.ReadWriteLogRecord

/**
 * A [LogRecordProcessor] that, surprise, does nothing.
 */
internal object NoopLogRecordProcessor : LogRecordProcessor {
    override fun onEmit(context: Context, logRecord: ReadWriteLogRecord) {}
}