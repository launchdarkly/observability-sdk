package com.launchdarkly.observability.sampling.utils

import com.launchdarkly.observability.sampling.ExportSampler
import com.launchdarkly.observability.sampling.SamplingConfig
import com.launchdarkly.observability.sampling.SamplingResult
import io.opentelemetry.sdk.logs.data.LogRecordData
import io.opentelemetry.sdk.trace.data.SpanData

class FakeExportSampler(
    private val sampleSpan: (SpanData) -> SamplingResult = { SamplingResult(true) },
    private val sampleLog: (LogRecordData) -> SamplingResult = { SamplingResult(true) },
    private val isSamplingEnabled: () -> Boolean = { true },
    private val setConfig: (SamplingConfig?) -> Unit = { }
) : ExportSampler {
    override fun sampleSpan(span: SpanData): SamplingResult = sampleSpan.invoke(span)
    override fun sampleLog(log: LogRecordData): SamplingResult = sampleLog.invoke(log)
    override fun isSamplingEnabled(): Boolean = isSamplingEnabled.invoke()
    override fun setConfig(config: SamplingConfig?) = setConfig.invoke(config)
}
