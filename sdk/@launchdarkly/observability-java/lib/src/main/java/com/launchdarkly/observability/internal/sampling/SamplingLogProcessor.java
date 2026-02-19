package com.launchdarkly.observability.internal.sampling;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.logs.LogRecordProcessor;
import io.opentelemetry.sdk.logs.ReadWriteLogRecord;

/**
 * A {@link LogRecordProcessor} that applies sampling logic before delegating
 * to another processor.
 */
public final class SamplingLogProcessor implements LogRecordProcessor {

    private final LogRecordProcessor delegate;
    private final CustomSampler sampler;

    public SamplingLogProcessor(LogRecordProcessor delegate, CustomSampler sampler) {
        this.delegate = delegate;
        this.sampler = sampler;
    }

    @Override
    public void onEmit(Context context, ReadWriteLogRecord logRecord) {
        if (!sampler.isSamplingEnabled()) {
            delegate.onEmit(context, logRecord);
            return;
        }

        CustomSampler.SamplingResult result = sampler.sampleLog(logRecord.toLogRecordData());
        if (!result.isSampled()) {
            return;
        }

        if (result.getAttributes() != null) {
            Attributes merged = Attributes.builder()
                    .putAll(logRecord.toLogRecordData().getAttributes())
                    .putAll(result.getAttributes())
                    .build();
            logRecord.setAllAttributes(merged);
        }
        delegate.onEmit(context, logRecord);
    }

    @Override
    public CompletableResultCode shutdown() {
        return delegate.shutdown();
    }

    @Override
    public CompletableResultCode forceFlush() {
        return delegate.forceFlush();
    }
}
