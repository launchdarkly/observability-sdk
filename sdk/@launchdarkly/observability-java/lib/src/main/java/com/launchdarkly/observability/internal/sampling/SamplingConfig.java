package com.launchdarkly.observability.internal.sampling;

import java.util.Collections;
import java.util.List;

/**
 * Sampling configuration received from the LaunchDarkly backend.
 */
public final class SamplingConfig {

    private final List<SpanSamplingConfig> spans;
    private final List<LogSamplingConfig> logs;

    public SamplingConfig(List<SpanSamplingConfig> spans, List<LogSamplingConfig> logs) {
        this.spans = spans != null ? spans : Collections.emptyList();
        this.logs = logs != null ? logs : Collections.emptyList();
    }

    public List<SpanSamplingConfig> getSpans() { return spans; }
    public List<LogSamplingConfig> getLogs() { return logs; }

    @Override
    public String toString() {
        return "SamplingConfig{spans=" + spans.size() + ", logs=" + logs.size() + "}";
    }

    // --- Match config types ---

    public static final class ValueMatch {
        private final String value;
        public ValueMatch(String value) { this.value = value; }
        public String getValue() { return value; }
    }

    public static final class RegexMatch {
        private final String pattern;
        public RegexMatch(String pattern) { this.pattern = pattern; }
        public String getPattern() { return pattern; }
    }

    /**
     * A match config is either a ValueMatch or a RegexMatch.
     * Stored as a pair: at most one of value/regex is non-null.
     */
    public static final class MatchConfig {
        private final String value;
        private final String regexPattern;

        private MatchConfig(String value, String regexPattern) {
            this.value = value;
            this.regexPattern = regexPattern;
        }

        public static MatchConfig ofValue(String value) {
            return new MatchConfig(value, null);
        }

        public static MatchConfig ofRegex(String pattern) {
            return new MatchConfig(null, pattern);
        }

        public boolean isRegex() { return regexPattern != null; }
        public String getValue() { return value; }
        public String getRegexPattern() { return regexPattern; }
    }

    public static final class AttributeMatchConfig {
        private final MatchConfig key;
        private final MatchConfig attribute;

        public AttributeMatchConfig(MatchConfig key, MatchConfig attribute) {
            this.key = key;
            this.attribute = attribute;
        }

        public MatchConfig getKey() { return key; }
        public MatchConfig getAttribute() { return attribute; }
    }

    public static final class SpanEventMatchConfig {
        private final MatchConfig name;
        private final List<AttributeMatchConfig> attributes;

        public SpanEventMatchConfig(MatchConfig name, List<AttributeMatchConfig> attributes) {
            this.name = name;
            this.attributes = attributes != null ? attributes : Collections.emptyList();
        }

        public MatchConfig getName() { return name; }
        public List<AttributeMatchConfig> getAttributes() { return attributes; }
    }

    public static final class SpanSamplingConfig {
        private final MatchConfig name;
        private final List<AttributeMatchConfig> attributes;
        private final List<SpanEventMatchConfig> events;
        private final int samplingRatio;

        public SpanSamplingConfig(
                MatchConfig name,
                List<AttributeMatchConfig> attributes,
                List<SpanEventMatchConfig> events,
                int samplingRatio
        ) {
            this.name = name;
            this.attributes = attributes != null ? attributes : Collections.emptyList();
            this.events = events != null ? events : Collections.emptyList();
            this.samplingRatio = samplingRatio;
        }

        public MatchConfig getName() { return name; }
        public List<AttributeMatchConfig> getAttributes() { return attributes; }
        public List<SpanEventMatchConfig> getEvents() { return events; }
        public int getSamplingRatio() { return samplingRatio; }
    }

    public static final class LogSamplingConfig {
        private final MatchConfig message;
        private final MatchConfig severityText;
        private final List<AttributeMatchConfig> attributes;
        private final int samplingRatio;

        public LogSamplingConfig(
                MatchConfig message,
                MatchConfig severityText,
                List<AttributeMatchConfig> attributes,
                int samplingRatio
        ) {
            this.message = message;
            this.severityText = severityText;
            this.attributes = attributes != null ? attributes : Collections.emptyList();
            this.samplingRatio = samplingRatio;
        }

        public MatchConfig getMessage() { return message; }
        public MatchConfig getSeverityText() { return severityText; }
        public List<AttributeMatchConfig> getAttributes() { return attributes; }
        public int getSamplingRatio() { return samplingRatio; }
    }
}
