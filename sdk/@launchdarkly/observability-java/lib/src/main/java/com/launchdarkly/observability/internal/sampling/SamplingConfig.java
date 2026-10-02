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
        private static final int MAX_REGEX_LENGTH = 128;
        private static final String ALLOWED_REGEX_CHARS =
                "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
                        + ".*+?^$|()[]{}\\-_:/ @#,";

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
            return new MatchConfig(null, pattern, compileBounded(pattern));
        }

        /**
         * Compiles a sampling regex once, or returns null when the rule must
         * not be used. The pattern comes from LaunchDarkly sampling config
         * (or a custom backend URL). A bad pattern never throws and never
         * matches. Only an allowlisted copy is compiled, length is capped,
         * and nested quantifiers and backreferences are rejected so a rule
         * cannot ReDoS the export path.
         */
        private static Pattern compileBounded(String pattern) {
            if (pattern == null || pattern.isEmpty() || pattern.length() > MAX_REGEX_LENGTH) {
                return null;
            }
            if (isCatastrophic(pattern)) {
                return null;
            }
            String allowlisted = allowlistedCopy(pattern);
            if (allowlisted == null) {
                return null;
            }
            try {
                // codeql[java/regex-injection]: allowlisted copy of sampling config, length-capped, nested quantifiers rejected
                return Pattern.compile(allowlisted);
            } catch (PatternSyntaxException ignored) {
                return null;
            }
        }

        private static String allowlistedCopy(String pattern) {
            StringBuilder copy = new StringBuilder(pattern.length());
            for (int i = 0; i < pattern.length(); i++) {
                int index = ALLOWED_REGEX_CHARS.indexOf(pattern.charAt(i));
                if (index < 0) {
                    return null;
                }
                copy.append(ALLOWED_REGEX_CHARS.charAt(index));
            }
            return copy.toString();
        }

        private static boolean isCatastrophic(String pattern) {
            boolean[] groupHasQuantifier = new boolean[pattern.length() + 1];
            int depth = 0;
            boolean prevWasQuantifier = false;
            boolean prevWasGroup = false;
            boolean prevGroupHadQuantifier = false;
            for (int i = 0; i < pattern.length(); i++) {
                char c = pattern.charAt(i);
                if (c == '\\') {
                    if (i + 1 >= pattern.length()) {
                        return true;
                    }
                    char escaped = pattern.charAt(++i);
                    if (escaped >= '1' && escaped <= '9') {
                        return true;
                    }
                    prevWasQuantifier = false;
                    prevWasGroup = false;
                    continue;
                }
                if (c == '(') {
                    if (i + 1 < pattern.length() && pattern.charAt(i + 1) == '?') {
                        i++;
                        if (i + 1 < pattern.length()) {
                            char flag = pattern.charAt(i + 1);
                            if (flag == ':' || flag == '=' || flag == '!') {
                                i++;
                            }
                        }
                    }
                    if (++depth >= groupHasQuantifier.length) {
                        return true;
                    }
                    groupHasQuantifier[depth] = false;
                    prevWasQuantifier = false;
                    prevWasGroup = false;
                    continue;
                }
                if (c == ')') {
                    if (depth <= 0) {
                        return true;
                    }
                    prevGroupHadQuantifier = groupHasQuantifier[depth];
                    depth--;
                    prevWasGroup = true;
                    prevWasQuantifier = false;
                    continue;
                }
                if (c == '?' && prevWasQuantifier) {
                    prevWasQuantifier = false;
                    continue;
                }
                if (c == '*' || c == '+' || c == '?' || c == '{') {
                    if (prevWasQuantifier || (prevWasGroup && prevGroupHadQuantifier)) {
                        return true;
                    }
                    if (c == '{') {
                        int end = pattern.indexOf('}', i);
                        if (end < 0) {
                            return true;
                        }
                        i = end;
                    }
                    if (depth > 0) {
                        groupHasQuantifier[depth] = true;
                    }
                    prevWasQuantifier = true;
                    prevWasGroup = false;
                    continue;
                }
                prevWasQuantifier = false;
                prevWasGroup = false;
            }
            return false;
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
