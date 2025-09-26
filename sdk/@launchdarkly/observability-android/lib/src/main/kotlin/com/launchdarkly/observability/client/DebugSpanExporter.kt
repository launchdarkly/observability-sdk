package com.launchdarkly.observability.client

import com.launchdarkly.logging.LDLogger
import io.opentelemetry.sdk.common.CompletableResultCode
import io.opentelemetry.sdk.trace.data.SpanData
import io.opentelemetry.sdk.trace.export.SpanExporter

class DebugSpanExporter(private val logger: LDLogger) : SpanExporter {

    override fun export(spans: Collection<SpanData>): CompletableResultCode {
        for (span in spans) {
            logger.info(span.toString())
        }
        return CompletableResultCode.ofSuccess()
    }

    override fun flush(): CompletableResultCode = CompletableResultCode.ofSuccess()
    override fun shutdown(): CompletableResultCode = CompletableResultCode.ofSuccess()
}