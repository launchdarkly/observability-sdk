package com.launchdarkly.observability.logs

import com.launchdarkly.observability.otlp.OtlpConfiguration
import com.launchdarkly.observability.otlp.OtlpHttpClient
import com.launchdarkly.observability.otlp.json.logs.JsonLogRecordAdapter
import com.launchdarkly.observability.otlp.json.logs.OtlpJsonExportLogsServiceRequest
import com.launchdarkly.observability.replay.transport.EventExporting
import com.launchdarkly.observability.replay.transport.EventQueueItem
import io.opentelemetry.sdk.logs.data.LogRecordData

/**
 * [EventExporting] implementation that sends queued [LogItemPayload]s to an OTLP/HTTP+JSON
 * logs endpoint.
 *
 * Mirrors the Swift `OtlpLogExporter`.
 */
class OtlpLogExporter(
    private val httpClient: OtlpHttpClient,
) : EventExporting {

    constructor(
        endpoint: String,
        config: OtlpConfiguration = OtlpConfiguration(),
    ) : this(OtlpHttpClient(endpoint = endpoint, config = config))

    override suspend fun export(items: List<EventQueueItem>) {
        val logRecords: List<LogRecordData> = items.mapNotNull { item ->
            (item.payload as? LogItemPayload)?.logRecord
        }
        if (logRecords.isEmpty()) return

        val body = JsonLogRecordAdapter.toJsonRequest(logRecords)
        httpClient.send(
            body = body,
            serializer = OtlpJsonExportLogsServiceRequest.serializer(),
        )
    }
}
