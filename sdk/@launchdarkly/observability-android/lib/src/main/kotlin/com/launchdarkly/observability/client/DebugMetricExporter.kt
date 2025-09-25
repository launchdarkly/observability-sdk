package com.launchdarkly.observability.client

import com.launchdarkly.logging.LDLogger
import io.opentelemetry.sdk.common.CompletableResultCode
import io.opentelemetry.sdk.metrics.InstrumentType
import io.opentelemetry.sdk.metrics.data.AggregationTemporality
import io.opentelemetry.sdk.metrics.data.MetricData
import io.opentelemetry.sdk.metrics.export.MetricExporter

class DebugMetricExporter(private val logger: LDLogger): MetricExporter {

    override fun export(metrics: Collection<MetricData?>): CompletableResultCode? {
        for (metric in metrics) {
            logger.info(metric.toString())
        }
        return CompletableResultCode.ofSuccess()
    }

    override fun flush(): CompletableResultCode = CompletableResultCode.ofSuccess()
    override fun shutdown(): CompletableResultCode = CompletableResultCode.ofSuccess()
    override fun getAggregationTemporality(instrumentType: InstrumentType): AggregationTemporality? = getAggregationTemporality(instrumentType)
}