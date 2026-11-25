package com.launchdarkly.observability.internal.sampling;

import com.launchdarkly.observability.internal.Constants;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.Value;
import io.opentelemetry.sdk.logs.data.LogRecordData;
import io.opentelemetry.sdk.trace.data.EventData;
import io.opentelemetry.sdk.trace.data.SpanData;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.IntPredicate;

/**
 * Applies the sampling rules from LaunchDarkly at export. The first rule that matches decides; an
 * item no rule matches is kept. Ported from the server-side Java plugin.
 */
public final class CustomSampler {
  private static final AttributeKey<Long> SAMPLING_RATIO_KEY =
      AttributeKey.longKey(Constants.SAMPLING_RATIO_ATTRIBUTE);

  private final AtomicReference<SamplingConfig> configRef = new AtomicReference<>();
  private final IntPredicate sampler;

  /** Creates a sampler that keeps one in {@code ratio} matching items at random. */
  public CustomSampler() {
    this(CustomSampler::defaultSampler);
  }

  /**
   * Creates a sampler with its own sampling decision. For tests.
   *
   * @param sampler given a rule's ratio, returns whether to keep the item
   */
  public CustomSampler(IntPredicate sampler) {
    this.sampler = sampler;
  }

  public void setConfig(SamplingConfig config) {
    configRef.set(config);
  }

  public boolean isSamplingEnabled() {
    return configRef.get() != null;
  }

  /** Decides whether to export a span. */
  public SamplingResult sampleSpan(SpanData span) {
    SamplingConfig config = configRef.get();
    if (config == null) {
      return SamplingResult.SAMPLED;
    }
    for (SamplingConfig.SpanSamplingConfig spanConfig : config.getSpans()) {
      if (matchesSpanConfig(spanConfig, span)) {
        return result(spanConfig.getSamplingRatio());
      }
    }
    return SamplingResult.SAMPLED;
  }

  /** Decides whether to export a log record. */
  public SamplingResult sampleLog(LogRecordData log) {
    SamplingConfig config = configRef.get();
    if (config == null) {
      return SamplingResult.SAMPLED;
    }
    for (SamplingConfig.LogSamplingConfig logConfig : config.getLogs()) {
      if (matchesLogConfig(logConfig, log)) {
        return result(logConfig.getSamplingRatio());
      }
    }
    return SamplingResult.SAMPLED;
  }

  private SamplingResult result(int ratio) {
    return new SamplingResult(sampler.test(ratio), Attributes.of(SAMPLING_RATIO_KEY, (long) ratio));
  }

  private static boolean matchesSpanConfig(SamplingConfig.SpanSamplingConfig config, SpanData span) {
    return matchesValue(config.getName(), span.getName())
        && matchesAttributes(config.getAttributes(), span.getAttributes())
        && matchesEvents(config.getEvents(), span.getEvents());
  }

  private static boolean matchesLogConfig(SamplingConfig.LogSamplingConfig config, LogRecordData log) {
    // The OTLP severityText field, as the other SDKs match, not the Severity enum name.
    if (!matchesValue(config.getSeverityText(), log.getSeverityText())) {
      return false;
    }
    if (config.getMessage() != null) {
      Value<?> body = log.getBodyValue();
      if (!matchesValue(config.getMessage(), body != null ? body.getValue() : null)) {
        return false;
      }
    }
    return matchesAttributes(config.getAttributes(), log.getAttributes());
  }

  /** Every event rule must match some event. */
  private static boolean matchesEvents(List<SamplingConfig.SpanEventMatchConfig> eventConfigs,
      List<EventData> events) {
    for (SamplingConfig.SpanEventMatchConfig eventConfig : eventConfigs) {
      boolean found = false;
      for (EventData event : events) {
        if (matchesValue(eventConfig.getName(), event.getName())
            && matchesAttributes(eventConfig.getAttributes(), event.getAttributes())) {
          found = true;
          break;
        }
      }
      if (!found) {
        return false;
      }
    }
    return true;
  }

  /** Every attribute rule must match some attribute. */
  private static boolean matchesAttributes(List<SamplingConfig.AttributeMatchConfig> configs, Attributes attributes) {
    if (configs.isEmpty()) {
      return true;
    }
    Map<AttributeKey<?>, Object> attributeMap = attributes.asMap();
    for (SamplingConfig.AttributeMatchConfig attributeConfig : configs) {
      boolean found = false;
      for (Map.Entry<AttributeKey<?>, Object> entry : attributeMap.entrySet()) {
        if (matchesValue(attributeConfig.getKey(), entry.getKey().getKey())
            && matchesValue(attributeConfig.getAttribute(), entry.getValue())) {
          found = true;
          break;
        }
      }
      if (!found) {
        return false;
      }
    }
    return true;
  }

  /** A missing rule matches anything, and a present rule never matches a missing value. */
  static boolean matchesValue(SamplingConfig.MatchConfig config, Object actual) {
    if (config == null) {
      return true;
    }
    return actual != null && config.matches(actual);
  }

  /** Ratio 0 or less keeps nothing, 1 keeps everything, and n keeps one in n at random. */
  static boolean defaultSampler(int ratio) {
    if (ratio <= 0) {
      return false;
    }
    return ratio == 1 || ThreadLocalRandom.current().nextInt(ratio) == 0;
  }

  /** Whether to keep an item, and the attributes to add when it is kept. */
  public static final class SamplingResult {
    static final SamplingResult SAMPLED = new SamplingResult(true, null);

    private final boolean sample;
    private final Attributes attributes;

    public SamplingResult(boolean sample, Attributes attributes) {
      this.sample = sample;
      this.attributes = attributes;
    }

    public boolean isSampled() {
      return sample;
    }

    /** The sampling attributes to add, or null when no rule matched. */
    public Attributes getAttributes() {
      return attributes;
    }
  }
}
