package com.launchdarkly.observability.client

import io.opentelemetry.context.Context
import io.opentelemetry.sdk.logs.LogRecordProcessor
import io.opentelemetry.sdk.logs.ReadWriteLogRecord
import java.util.concurrent.ConcurrentHashMap

class RoutingLogRecordProcessor(
    private val fallthroughProcessor: LogRecordProcessor = NoopLogRecordProcessor.instance
) : LogRecordProcessor {
    private val processors = ConcurrentHashMap<String, LogRecordProcessor>()

    fun registerProcessor(scopeName: String, processor: LogRecordProcessor) {
        processors[scopeName] = processor
    }

    override fun onEmit(context: Context, logRecord: ReadWriteLogRecord) {
        val scopeName = logRecord.instrumentationScopeInfo.name
        val processor = processors[scopeName] ?: fallthroughProcessor
        processor.onEmit(context, logRecord)
    }
}