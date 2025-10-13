package com.launchdarkly.observability.client

import io.opentelemetry.sdk.common.CompletableResultCode
import io.opentelemetry.sdk.logs.data.LogRecordData
import io.opentelemetry.sdk.logs.export.LogRecordExporter

/**
 * A log record exporter that conditionally forwards logs based on their source.
 * This allows different filtering rules for crashes vs normal logs.
 *
 * @property delegate The underlying exporter to forward logs to
 * @property allowNormalLogs Whether to allow normal application logs
 * @property allowCrashes Whether to allow crash logs from OpenTelemetry's CrashReporter
 */
class ConditionalLogRecordExporter(
    private val delegate: LogRecordExporter,
    private val allowNormalLogs: Boolean,
    private val allowCrashes: Boolean
) : LogRecordExporter {
    
    override fun export(logs: Collection<LogRecordData>): CompletableResultCode {
        val filteredLogs = logs.filter { logRecord ->
            // Check if this is a crash log (from OpenTelemetry's CrashReporter)
            val instrumentationScopeName = logRecord.instrumentationScopeInfo.name
            val isCrashLog = instrumentationScopeName == "io.opentelemetry.crash"
            
            when {
                isCrashLog -> allowCrashes
                else -> allowNormalLogs
            }
        }
        
        return if (filteredLogs.isNotEmpty()) {
            delegate.export(filteredLogs)
        } else {
            CompletableResultCode.ofSuccess()
        }
    }
    
    override fun flush(): CompletableResultCode = delegate.flush()
    
    override fun shutdown(): CompletableResultCode = delegate.shutdown()
}
