package com.launchdarkly.observability.sampling

import io.opentelemetry.sdk.logs.data.LogRecordData
import io.opentelemetry.sdk.trace.data.SpanData

/**
 * Samples nothing out. Used by the OTel-only product, which does not fetch remote sampling
 * configuration; [isSamplingEnabled] returns false so the processors skip sampling entirely.
 */
internal object NoopExportSampler : ExportSampler {
    override fun sampleSpan(span: SpanData): SamplingResult = SamplingResult(sample = true)

    override fun sampleLog(log: LogRecordData): SamplingResult = SamplingResult(sample = true)

    override fun isSamplingEnabled(): Boolean = false

    override fun setConfig(config: SamplingConfig?) {}
}
