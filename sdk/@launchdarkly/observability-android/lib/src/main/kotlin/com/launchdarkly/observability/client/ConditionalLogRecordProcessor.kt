package com.launchdarkly.observability.client

import io.opentelemetry.context.Context
import io.opentelemetry.sdk.common.CompletableResultCode
import io.opentelemetry.sdk.logs.LogRecordProcessor
import io.opentelemetry.sdk.logs.ReadWriteLogRecord

/**
 * Filters log records before they reach downstream processors based on their instrumentation scope.
 *
 * Crash logs emitted by OpenTelemetry's crash instrumentation use the "io.opentelemetry.crash" scope.
 * This processor can drop those logs independently from normal application logs.
 */
class ConditionalLogRecordProcessor(
    private val delegate: LogRecordProcessor,
    private val allowNormalLogs: Boolean,
    private val allowCrashes: Boolean
) : LogRecordProcessor {

    override fun onEmit(context: Context, logRecord: ReadWriteLogRecord) {
        if (!shouldEmitLog(logRecord)) {
            return
        }

        delegate.onEmit(context, logRecord)
    }

    override fun forceFlush(): CompletableResultCode = delegate.forceFlush()

    override fun shutdown(): CompletableResultCode = delegate.shutdown()

    override fun close() {
        delegate.close()
    }

    private fun shouldEmitLog(logRecord: ReadWriteLogRecord): Boolean {
        // Check if this is a crash log from OpenTelemetry's CrashReporterInstrumentation
        val instrumentationScopeName = logRecord.instrumentationScopeInfo.name
        val isCrashLog = instrumentationScopeName == CRASH_INSTRUMENTATION_SCOPE

        return when {
            isCrashLog -> allowCrashes
            else -> allowNormalLogs
        }
    }

    private companion object {
        const val CRASH_INSTRUMENTATION_SCOPE = "io.opentelemetry.crash"
    }
}
