package com.launchdarkly.observability.client

import com.launchdarkly.logging.LDLogger
import io.opentelemetry.sdk.common.CompletableResultCode
import io.opentelemetry.sdk.logs.data.LogRecordData
import io.opentelemetry.sdk.logs.export.LogRecordExporter

class DebugLogExporter(private val logger: LDLogger) : LogRecordExporter {

    override fun export(logRecords: Collection<LogRecordData>): CompletableResultCode {
        for (record in logRecords) {
            logger.info(record.toString()) // TODO: Figure out why logger.debug is being blocked by Log.isLoggable is adapter.
        }
        return CompletableResultCode.ofSuccess()
    }

    override fun flush(): CompletableResultCode = CompletableResultCode.ofSuccess()
    override fun shutdown(): CompletableResultCode = CompletableResultCode.ofSuccess()
}