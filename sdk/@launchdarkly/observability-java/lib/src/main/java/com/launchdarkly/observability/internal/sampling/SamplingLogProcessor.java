package com.launchdarkly.observability.internal.sampling;

import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.logs.LogRecordProcessor;
import io.opentelemetry.sdk.logs.ReadWriteLogRecord;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A {@link LogRecordProcessor} that applies sampling logic before delegating
 * to another processor.
 *
 * <p>{@code onEmit} runs on the thread that logged, usually a request thread,
 * so a sampling failure (for example a regex that overflows the stack on a
 * long message) is logged once and the record is kept rather than thrown
 * into the caller.</p>
 */
public final class SamplingLogProcessor implements LogRecordProcessor {

    private static final Logger log = Logger.getLogger(SamplingLogProcessor.class.getName());

    private final LogRecordProcessor delegate;
    private final CustomSampler sampler;
    private final AtomicBoolean failureLogged = new AtomicBoolean();

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

        CustomSampler.SamplingResult result;
        try {
            result = sampler.sampleLog(logRecord.toLogRecordData());
        } catch (RuntimeException | StackOverflowError e) {
            if (failureLogged.compareAndSet(false, true)) {
                log.log(Level.WARNING, "Log sampling failed; exporting records unsampled", e);
            }
            delegate.onEmit(context, logRecord);
            return;
        }
        if (!result.isSampled()) {
            return;
        }

        if (result.getAttributes() != null) {
            // Sets or overrides only the sampling attributes.
            logRecord.setAllAttributes(result.getAttributes());
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
