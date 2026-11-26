package com.launchdarkly.observability.internal.sampling;

import com.launchdarkly.logging.LDLogger;

import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.logs.LogRecordProcessor;
import io.opentelemetry.sdk.logs.ReadWriteLogRecord;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Applies sampling to each log record before passing it on. {@code onEmit} runs on the thread that
 * logged, often the UI thread, so a sampling failure (such as a regex that overflows the stack on a
 * long message) is logged once and the record is kept, rather than thrown into the caller.
 */
public final class SamplingLogProcessor implements LogRecordProcessor {
  private final LogRecordProcessor delegate;
  private final CustomSampler sampler;
  private final LDLogger logger;
  private final AtomicBoolean failureLogged = new AtomicBoolean();

  public SamplingLogProcessor(LogRecordProcessor delegate, CustomSampler sampler, LDLogger logger) {
    this.delegate = delegate;
    this.sampler = sampler;
    this.logger = logger;
  }

  @Override
  public void onEmit(Context context, ReadWriteLogRecord logRecord) {
    if (sampler.isSamplingEnabled()) {
      CustomSampler.SamplingResult result;
      try {
        result = sampler.sampleLog(logRecord.toLogRecordData());
      } catch (RuntimeException | StackOverflowError e) {
        if (failureLogged.compareAndSet(false, true)) {
          logger.warn("Log sampling failed, so records are exported unsampled: {}", e.toString());
        }
        result = CustomSampler.SamplingResult.SAMPLED;
      }
      if (!result.isSampled()) {
        return;
      }
      if (result.getAttributes() != null) {
        logRecord.setAllAttributes(result.getAttributes());
      }
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
