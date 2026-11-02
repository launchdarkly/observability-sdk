package com.launchdarkly.observability.internal;

import io.opentelemetry.api.logs.Severity;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.logs.LogRecordProcessor;
import io.opentelemetry.sdk.logs.ReadWriteLogRecord;

/**
 * Drops log records below a minimum severity before they reach the next
 * processor. A record with no severity set is treated as {@code TRACE}.
 */
public final class LogLevelFilterProcessor implements LogRecordProcessor {

    private final LogRecordProcessor delegate;
    private final int minimumSeverityNumber;

    public LogLevelFilterProcessor(LogRecordProcessor delegate, int minimumSeverityNumber) {
        this.delegate = delegate;
        this.minimumSeverityNumber = minimumSeverityNumber;
    }

    @Override
    public void onEmit(Context context, ReadWriteLogRecord logRecord) {
        Severity severity = logRecord.toLogRecordData().getSeverity();
        int number = severity == null || severity == Severity.UNDEFINED_SEVERITY_NUMBER
                ? Severity.TRACE.getSeverityNumber()
                : severity.getSeverityNumber();
        if (number < minimumSeverityNumber) {
            return;
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
