package com.launchdarkly.observability.internal.sampling;

import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

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

    /**
     * A match config is either an exact value or a regular expression.
     * The pattern is compiled once so export-time matching does not recompile
     * it on every span or log.
     */
    public static final class MatchConfig {
        private final String value;
        private final String regexPattern;
        private final Pattern compiledRegex;

        private MatchConfig(String value, String regexPattern, Pattern compiledRegex) {
            this.value = value;
            this.regexPattern = regexPattern;
            this.compiledRegex = compiledRegex;
        }

        public static MatchConfig ofValue(String value) {
            return new MatchConfig(value, null, null);
        }

        public static MatchConfig ofRegex(String pattern) {
            Pattern compiled = null;
            try {
                compiled = Pattern.compile(pattern);
            } catch (PatternSyntaxException ignored) {
                // An invalid rule never matches, instead of failing the export.
            }
            return new MatchConfig(null, pattern, compiled);
        }

        public boolean isRegex() { return regexPattern != null; }
        public String getValue() { return value; }
        public String getRegexPattern() { return regexPattern; }

        public boolean matches(String actual) {
            if (actual == null) {
                return false;
            }
            if (regexPattern != null) {
                return compiledRegex != null && compiledRegex.matcher(actual).matches();
            }
            return value != null && value.equals(actual);
        }
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
