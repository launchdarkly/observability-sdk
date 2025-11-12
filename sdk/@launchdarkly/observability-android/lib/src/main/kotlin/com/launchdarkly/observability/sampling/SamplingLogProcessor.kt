package com.launchdarkly.observability.sampling

import io.opentelemetry.api.common.Attributes
import io.opentelemetry.context.Context
import io.opentelemetry.sdk.common.CompletableResultCode
import io.opentelemetry.sdk.logs.LogRecordProcessor
import io.opentelemetry.sdk.logs.ReadWriteLogRecord

/**
 * A [LogRecordProcessor] that applies sampling logic before delegating to another [LogRecordProcessor].
 *
 * This processor drops unsampled log records so they are never enqueued in downstream processors,
 * reducing buffering and export overhead for logs that would have been filtered out later.
 *
 * @param delegate The underlying [LogRecordProcessor] that should receive sampled log records.
 * @param sampler The sampler that decides whether a log record should be processed.
 */
class SamplingLogProcessor(
    private val delegate: LogRecordProcessor,
    private val sampler: ExportSampler
) : LogRecordProcessor {

    override fun onEmit(context: Context, logRecord: ReadWriteLogRecord) {
        if (!sampler.isSamplingEnabled()) {
            delegate.onEmit(context, logRecord)
            return
        }

        val samplingResult = sampler.sampleLog(logRecord.toLogRecordData())
        if (!samplingResult.sample) {
            return
        }

        samplingResult.attributes?.let { addSamplingAttributes(logRecord, it) }
        delegate.onEmit(context, logRecord)
    }

    override fun shutdown(): CompletableResultCode {
        return delegate.shutdown()
    }

    override fun forceFlush(): CompletableResultCode {
        return delegate.forceFlush()
    }

    override fun close() {
        delegate.close()
    }

    private fun addSamplingAttributes(
        logRecord: ReadWriteLogRecord,
        samplingAttributes: Attributes
    ) {
        val mergedAttributes = Attributes.builder()
            .putAll(logRecord.attributes)
            .putAll(samplingAttributes)
            .build()
        logRecord.setAllAttributes(mergedAttributes)
    }
}
