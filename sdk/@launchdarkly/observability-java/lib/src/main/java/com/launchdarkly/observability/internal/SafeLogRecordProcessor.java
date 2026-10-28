package com.launchdarkly.observability.internal;

import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.logs.LogRecordProcessor;
import io.opentelemetry.sdk.logs.ReadWriteLogRecord;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Wraps a log record processor from {@code ObservabilityOptions} so an
 * exception it throws is logged instead of reaching the code that logged, or
 * stopping the processors after it, the exporter included.
 */
final class SafeLogRecordProcessor implements LogRecordProcessor {

    private static final Logger log = Logger.getLogger(SafeLogRecordProcessor.class.getName());

    private final LogRecordProcessor delegate;
    private final AtomicBoolean failureLogged = new AtomicBoolean();

    SafeLogRecordProcessor(LogRecordProcessor delegate) {
        this.delegate = delegate;
    }

    @Override
    public void onEmit(Context context, ReadWriteLogRecord logRecord) {
        try {
            delegate.onEmit(context, logRecord);
        } catch (RuntimeException | LinkageError e) {
            logFailure("onEmit", e);
        }
    }

    @Override
    public CompletableResultCode forceFlush() {
        try {
            CompletableResultCode result = delegate.forceFlush();
            return result != null ? result : CompletableResultCode.ofSuccess();
        } catch (RuntimeException | LinkageError e) {
            logFailure("forceFlush", e);
            return CompletableResultCode.ofFailure();
        }
    }

    @Override
    public CompletableResultCode shutdown() {
        try {
            CompletableResultCode result = delegate.shutdown();
            return result != null ? result : CompletableResultCode.ofSuccess();
        } catch (RuntimeException | LinkageError e) {
            logFailure("shutdown", e);
            return CompletableResultCode.ofFailure();
        }
    }

    /** See {@link SafeSpanProcessor}: only the first failure is logged at WARNING. */
    private void logFailure(String method, Throwable e) {
        Level level = failureLogged.compareAndSet(false, true) ? Level.WARNING : Level.FINE;
        log.log(level, "Log record processor " + delegate.getClass().getName() + " threw from " + method
                + "; the record is still exported", e);
    }

    @Override
    public String toString() {
        return "SafeLogRecordProcessor{" + delegate + "}";
    }
}
