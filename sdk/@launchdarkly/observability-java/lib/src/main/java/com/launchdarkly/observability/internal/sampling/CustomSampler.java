package com.launchdarkly.observability.internal.sampling;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.sdk.logs.data.LogRecordData;
import io.opentelemetry.sdk.trace.data.EventData;
import io.opentelemetry.sdk.trace.data.SpanData;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.IntPredicate;
import java.util.regex.Pattern;

/**
 * Applies export-time sampling based on a configuration fetched from the backend.
 */
public final class CustomSampler {

    private static final String ATTR_SAMPLING_RATIO = "highlight.sampling.ratio";
    private static final AttributeKey<Long> SAMPLING_RATIO_KEY = AttributeKey.longKey(ATTR_SAMPLING_RATIO);

    private final AtomicReference<SamplingConfig> configRef = new AtomicReference<>();
    private final IntPredicate sampler;

    public CustomSampler() {
        this(CustomSampler::defaultSampler);
    }

    /** Visible for testing — inject a deterministic sampler. */
    public CustomSampler(IntPredicate sampler) {
        this.sampler = sampler;
    }

    public void setConfig(SamplingConfig config) {
        configRef.set(config);
    }

    public boolean isSamplingEnabled() {
        return configRef.get() != null;
    }

    // --- Span sampling ---

    public SamplingResult sampleSpan(SpanData span) {
        SamplingConfig config = configRef.get();
        if (config == null) {
            return SamplingResult.SAMPLED;
        }
        for (SamplingConfig.SpanSamplingConfig spanConfig : config.getSpans()) {
            if (matchesSpanConfig(spanConfig, span)) {
                boolean keep = sampler.test(spanConfig.getSamplingRatio());
                return new SamplingResult(keep,
                        Attributes.of(SAMPLING_RATIO_KEY, (long) spanConfig.getSamplingRatio()));
            }
        }
        return SamplingResult.SAMPLED;
    }

    // --- Log sampling ---

    public SamplingResult sampleLog(LogRecordData log) {
        SamplingConfig config = configRef.get();
        if (config == null) {
            return SamplingResult.SAMPLED;
        }
        for (SamplingConfig.LogSamplingConfig logConfig : config.getLogs()) {
            if (matchesLogConfig(logConfig, log)) {
                boolean keep = sampler.test(logConfig.getSamplingRatio());
                return new SamplingResult(keep,
                        Attributes.of(SAMPLING_RATIO_KEY, (long) logConfig.getSamplingRatio()));
            }
        }
        return SamplingResult.SAMPLED;
    }

    // --- Matching logic ---

    private boolean matchesSpanConfig(SamplingConfig.SpanSamplingConfig config, SpanData span) {
        if (config.getName() != null) {
            if (!matchesValue(config.getName(), span.getName())) {
                return false;
            }
        }
        if (!matchesAttributes(config.getAttributes(), span.getAttributes())) {
            return false;
        }
        if (!config.getEvents().isEmpty()) {
            if (!matchesEvents(config.getEvents(), span.getEvents())) {
                return false;
            }
        }
        return true;
    }

    private boolean matchesLogConfig(SamplingConfig.LogSamplingConfig config, LogRecordData log) {
        if (config.getSeverityText() != null) {
            String severity = log.getSeverity() != null ? log.getSeverity().name() : null;
            if (!matchesValue(config.getSeverityText(), severity)) {
                return false;
            }
        }
        if (config.getMessage() != null) {
            String body = log.getBody() != null ? log.getBody().asString() : null;
            if (!matchesValue(config.getMessage(), body)) {
                return false;
            }
        }
        return matchesAttributes(config.getAttributes(), log.getAttributes());
    }

    private boolean matchesEvents(List<SamplingConfig.SpanEventMatchConfig> eventConfigs, List<EventData> events) {
        for (SamplingConfig.SpanEventMatchConfig eventConfig : eventConfigs) {
            boolean found = false;
            for (EventData event : events) {
                if (matchesEventConfig(eventConfig, event)) {
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

    private boolean matchesEventConfig(SamplingConfig.SpanEventMatchConfig config, EventData event) {
        if (config.getName() != null) {
            if (!matchesValue(config.getName(), event.getName())) {
                return false;
            }
        }
        return matchesAttributes(config.getAttributes(), event.getAttributes());
    }

    private boolean matchesAttributes(List<SamplingConfig.AttributeMatchConfig> configs, Attributes attributes) {
        for (SamplingConfig.AttributeMatchConfig attrConfig : configs) {
            boolean found = false;
            Map<AttributeKey<?>, Object> attrMap = attributes.asMap();
            for (Map.Entry<AttributeKey<?>, Object> entry : attrMap.entrySet()) {
                String key = entry.getKey().getKey();
                String value = entry.getValue() != null ? entry.getValue().toString() : null;
                if (matchesValue(attrConfig.getKey(), key) && matchesValue(attrConfig.getAttribute(), value)) {
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

    static boolean matchesValue(SamplingConfig.MatchConfig config, String actual) {
        if (config == null) {
            return true;
        }
        if (actual == null) {
            return false;
        }
        if (config.isRegex()) {
            return Pattern.matches(config.getRegexPattern(), actual);
        } else {
            return config.getValue().equals(actual);
        }
    }

    /**
     * Determine if an item should be sampled based on the sampling ratio.
     * A ratio of 0 means never sample. A ratio of 1 means always sample.
     * Otherwise, sample 1 in every {@code ratio} items.
     */
    static boolean defaultSampler(int ratio) {
        if (ratio <= 0) return false;
        if (ratio == 1) return true;
        return new Random().nextInt(ratio) == 0;
    }

    public static final class SamplingResult {
        static final SamplingResult SAMPLED = new SamplingResult(true, null);

        private final boolean sample;
        private final Attributes attributes;

        public SamplingResult(boolean sample, Attributes attributes) {
            this.sample = sample;
            this.attributes = attributes;
        }

        public boolean isSampled() { return sample; }
        public Attributes getAttributes() { return attributes; }
    }
}
