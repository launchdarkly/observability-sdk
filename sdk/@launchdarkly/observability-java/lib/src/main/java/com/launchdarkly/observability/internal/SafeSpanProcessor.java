package com.launchdarkly.observability.internal;

import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.trace.ReadWriteSpan;
import io.opentelemetry.sdk.trace.ReadableSpan;
import io.opentelemetry.sdk.trace.SpanProcessor;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Wraps a span processor from {@code ObservabilityOptions} so an exception it
 * throws is logged instead of reaching the code that started or ended the
 * span, or stopping the processors after it, the exporter included.
 */
final class SafeSpanProcessor implements SpanProcessor {

    private static final Logger log = Logger.getLogger(SafeSpanProcessor.class.getName());

    private final SpanProcessor delegate;
    private final AtomicBoolean failureLogged = new AtomicBoolean();

    SafeSpanProcessor(SpanProcessor delegate) {
        this.delegate = delegate;
    }

    @Override
    public void onStart(Context parentContext, ReadWriteSpan span) {
        try {
            delegate.onStart(parentContext, span);
        } catch (RuntimeException | LinkageError e) {
            logFailure("onStart", e);
        }
    }

    @Override
    public boolean isStartRequired() {
        try {
            return delegate.isStartRequired();
        } catch (RuntimeException | LinkageError e) {
            logFailure("isStartRequired", e);
            return true;
        }
    }

    @Override
    public void onEnd(ReadableSpan span) {
        try {
            delegate.onEnd(span);
        } catch (RuntimeException | LinkageError e) {
            logFailure("onEnd", e);
        }
    }

    @Override
    public boolean isEndRequired() {
        try {
            return delegate.isEndRequired();
        } catch (RuntimeException | LinkageError e) {
            logFailure("isEndRequired", e);
            return true;
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

    /** The first failure is logged with its stack trace; later ones only at FINE, so a processor failing on every span does not flood the log. */
    private void logFailure(String method, Throwable e) {
        Level level = failureLogged.compareAndSet(false, true) ? Level.WARNING : Level.FINE;
        log.log(level, "Span processor " + delegate.getClass().getName() + " threw from " + method
                + "; the span is still exported", e);
    }

    @Override
    public String toString() {
        return "SafeSpanProcessor{" + delegate + "}";
    }
}
