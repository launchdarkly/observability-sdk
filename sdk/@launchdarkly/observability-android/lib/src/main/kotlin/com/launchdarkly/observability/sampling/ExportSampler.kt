package com.launchdarkly.observability.sampling

import io.opentelemetry.sdk.logs.data.LogRecordData
import io.opentelemetry.sdk.trace.data.SpanData

/**
 * A sampler that runs during the export phase of the trace pipeline.
 */
interface ExportSampler {
    /**
     * Returns a sampling result for a span.
     *
     * @param span The span to sample.
     */
    fun sampleSpan(span: SpanData): SamplingResult

    /**
     * Returns a sampling result for a log.
     *
     * @param log The log to sample.
     */
    fun sampleLog(log: LogRecordData): SamplingResult

    /**
     * Returns true if sampling is enabled. If there are no sampling configurations, then sampling can be skipped.
     */
    fun isSamplingEnabled(): Boolean

    fun setConfig(config: SamplingConfig?)
}