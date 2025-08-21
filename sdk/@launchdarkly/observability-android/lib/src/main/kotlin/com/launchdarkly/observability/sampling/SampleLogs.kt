package com.launchdarkly.observability.sampling

import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.common.Value
import io.opentelemetry.sdk.logs.data.LogRecordData

fun sampleLogs(
    items: List<LogRecordData>,
    sampler: ExportSampler
): List<LogRecordData> {

    if (!sampler.isSamplingEnabled()) {
        return items
    }

    val sampledLogs = mutableListOf<LogRecordData>()

    for (item in items) {
        val sampleResult = sampler.sampleLog(item)
        if (sampleResult.sample) {
            if (sampleResult.attributes != null) {
                sampledLogs.add(
                    cloneLogRecordWithAttributes(item, sampleResult.attributes)
                )
            } else {
                sampledLogs.add(item)
            }
        }
        // If not sampled, we simply don't include it in the result
    }

    return sampledLogs
}

private fun cloneLogRecordWithAttributes(
    log: LogRecordData,
    attributes: Attributes
): LogRecordData {
    return object : LogRecordData by log {
        override fun getBodyValue(): Value<*>? = log.getBodyValue()
        override fun getEventName(): String? = log.eventName

        override fun getAttributes(): Attributes {
            val builder = Attributes.builder()
            builder.putAll(log.attributes)
            builder.putAll(attributes)
            return builder.build()
        }
    }
}