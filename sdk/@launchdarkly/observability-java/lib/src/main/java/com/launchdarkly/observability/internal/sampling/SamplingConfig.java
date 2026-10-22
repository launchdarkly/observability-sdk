package com.launchdarkly.observability.internal.sampling;

import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;
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
     *
     * <p>Matching follows the Go and .NET SDKs: a numeric value matches a
     * numeric attribute of equal value whether it is a long or a double, a
     * boolean matches a boolean, a string matches the value's text, and a
     * regex only matches string values.</p>
     */
    public static final class MatchConfig {
        private static final Logger log = Logger.getLogger(MatchConfig.class.getName());
        private static final double EPSILON = 0.0000000000000001;

        private final Object value;
        private final String regexPattern;
        private final Pattern compiledRegex;

        private MatchConfig(Object value, String regexPattern, Pattern compiledRegex) {
            this.value = value;
            this.regexPattern = regexPattern;
            this.compiledRegex = compiledRegex;
        }

        /**
         * An exact-value match. The value is a {@link String}, a
         * {@link Boolean} or a {@link Number}; any other type never matches.
         */
        public static MatchConfig ofValue(Object value) {
            return new MatchConfig(value, null, null);
        }

        public static MatchConfig ofRegex(String pattern) {
            return new MatchConfig(null, pattern, compile(pattern));
        }

        /**
         * Compiles a sampling regex as sent by the backend, like the Go SDK.
         * A pattern that does not compile is logged and the rule is skipped:
         * it never matches, and parsing the rest of the config carries on.
         */
        private static Pattern compile(String pattern) {
            if (pattern == null) {
                return null;
            }
            try {
                return Pattern.compile(pattern);
            } catch (PatternSyntaxException e) {
                log.warning("Skipping sampling rule with invalid regex " + pattern + ": " + e.getDescription());
                return null;
            }
        }

        public boolean isRegex() { return regexPattern != null; }
        public Object getValue() { return value; }
        public String getRegexPattern() { return regexPattern; }

        /**
         * @param actual a span name, log body, attribute key or attribute
         *               value as recorded: String, Boolean, Long, Double or
         *               a list of those
         */
        public boolean matches(Object actual) {
            if (actual == null) {
                return false;
            }
            if (regexPattern != null) {
                return compiledRegex != null
                        && actual instanceof String
                        && compiledRegex.matcher((String) actual).find();
            }
            if (value instanceof String) {
                return value.equals(actual instanceof String ? actual : String.valueOf(actual));
            }
            if (value instanceof Boolean) {
                return value.equals(actual);
            }
            if (value instanceof Number) {
                return actual instanceof Number
                        && Math.abs(((Number) value).doubleValue() - ((Number) actual).doubleValue()) < EPSILON;
            }
            return false;
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
