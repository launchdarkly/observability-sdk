package com.launchdarkly.observability.metrics

import com.launchdarkly.observability.otlp.OtlpConfiguration
import com.launchdarkly.observability.otlp.OtlpHttpClient
import com.launchdarkly.observability.otlp.json.metrics.JsonMetricsAdapter
import com.launchdarkly.observability.otlp.json.metrics.OtlpJsonExportMetricsServiceRequest
import com.launchdarkly.observability.replay.transport.EventExporting
import com.launchdarkly.observability.replay.transport.EventQueueItem
import io.opentelemetry.sdk.metrics.data.MetricData

/**
 * [EventExporting] implementation that sends queued [MetricItemPayload]s to an OTLP/HTTP+JSON
 * metrics endpoint.
 *
 * Mirrors the Swift `OtlpMetricEventExporter`.
 */
class OtlpMetricExporter(
    private val httpClient: OtlpHttpClient,
) : EventExporting {

    constructor(
        endpoint: String,
        config: OtlpConfiguration = OtlpConfiguration(),
    ) : this(OtlpHttpClient(endpoint = endpoint, config = config))

    override suspend fun export(items: List<EventQueueItem>) {
        val metrics: List<MetricData> = items.mapNotNull { item ->
            (item.payload as? MetricItemPayload)?.metricData
        }
        if (metrics.isEmpty()) return

        val body = JsonMetricsAdapter.toJsonRequest(metrics)
        httpClient.send(
            body = body,
            serializer = OtlpJsonExportMetricsServiceRequest.serializer(),
        )
    }
}
