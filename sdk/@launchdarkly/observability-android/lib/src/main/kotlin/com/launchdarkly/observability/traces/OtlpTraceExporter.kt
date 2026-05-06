package com.launchdarkly.observability.traces

import com.launchdarkly.observability.otlp.OtlpConfiguration
import com.launchdarkly.observability.otlp.OtlpHttpClient
import com.launchdarkly.observability.otlp.json.traces.JsonSpanAdapter
import com.launchdarkly.observability.otlp.json.traces.OtlpJsonExportTraceServiceRequest
import com.launchdarkly.observability.replay.transport.EventExporting
import com.launchdarkly.observability.replay.transport.EventQueueItem
import io.opentelemetry.sdk.trace.data.SpanData

/**
 * [EventExporting] implementation that sends queued [SpanItemPayload]s to an OTLP/HTTP+JSON
 * traces endpoint.
 *
 * Mirrors the Swift `OtlpTraceEventExporter`.
 */
class OtlpTraceExporter(
    private val httpClient: OtlpHttpClient,
) : EventExporting {

    constructor(
        endpoint: String,
        config: OtlpConfiguration = OtlpConfiguration(),
    ) : this(OtlpHttpClient(endpoint = endpoint, config = config))

    override suspend fun export(items: List<EventQueueItem>) {
        val spans: List<SpanData> = items.mapNotNull { item ->
            (item.payload as? SpanItemPayload)?.spanData
        }
        if (spans.isEmpty()) return

        val body = JsonSpanAdapter.toJsonRequest(spans)
        httpClient.send(
            body = body,
            serializer = OtlpJsonExportTraceServiceRequest.serializer(),
        )
    }
}
