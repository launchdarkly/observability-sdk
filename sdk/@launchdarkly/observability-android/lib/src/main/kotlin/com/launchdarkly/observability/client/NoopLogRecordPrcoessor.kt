package com.launchdarkly.observability.client

import io.opentelemetry.context.Context
import io.opentelemetry.sdk.logs.LogRecordProcessor
import io.opentelemetry.sdk.logs.ReadWriteLogRecord

/**
 * A [LogRecordProcessor] that, surprise, does nothing.
 */
internal class NoopLogRecordProcessor private constructor() : LogRecordProcessor {
    override fun onEmit(context: Context, logRecord: ReadWriteLogRecord) {}

    companion object {
        private val INSTANCE = NoopLogRecordProcessor()

        val instance: LogRecordProcessor
            get() = INSTANCE
    }
}
