package com.launchdarkly.observability.internal.sampling;

import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Sampling rules received from LaunchDarkly. Ported from the server-side Java plugin, whose matching
 * follows the Go and .NET SDKs.
 */
public final class SamplingConfig {
  private final List<SpanSamplingConfig> spans;
  private final List<LogSamplingConfig> logs;

  public SamplingConfig(List<SpanSamplingConfig> spans, List<LogSamplingConfig> logs) {
    this.spans = spans != null ? spans : Collections.<SpanSamplingConfig>emptyList();
    this.logs = logs != null ? logs : Collections.<LogSamplingConfig>emptyList();
  }

  public List<SpanSamplingConfig> getSpans() {
    return spans;
  }

  public List<LogSamplingConfig> getLogs() {
    return logs;
  }

  @Override
  public String toString() {
    return "SamplingConfig{spans=" + spans.size() + ", logs=" + logs.size() + "}";
  }

  /**
   * An exact value or a regular expression. The pattern is compiled once, so matching at export
   * does not recompile it.
   * <p>
   * A numeric value matches a numeric attribute of equal value, whether long or double; a boolean
   * matches a boolean; a string matches the value's text; and a regex matches only strings. A
   * regex that does not compile matches nothing.
   */
  public static final class MatchConfig {
    private static final double EPSILON = 0.0000000000000001;

    private final Object value;
    private final String regexPattern;
    private final Pattern compiledRegex;

    private MatchConfig(Object value, String regexPattern, Pattern compiledRegex) {
      this.value = value;
      this.regexPattern = regexPattern;
      this.compiledRegex = compiledRegex;
    }

    /** An exact-value match: a {@link String}, {@link Boolean} or {@link Number}. Any other type never matches. */
    public static MatchConfig ofValue(Object value) {
      return new MatchConfig(value, null, null);
    }

    /** A regular-expression match, which finds the pattern anywhere in a string value. */
    public static MatchConfig ofRegex(String pattern) {
      Pattern compiled;
      try {
        compiled = Pattern.compile(pattern);
      } catch (PatternSyntaxException e) {
        compiled = null;
      }
      return new MatchConfig(null, pattern, compiled);
    }

    public boolean isRegex() {
      return regexPattern != null;
    }

    /** False for a regex that did not compile, which never matches. */
    public boolean isValid() {
      return regexPattern == null || compiledRegex != null;
    }

    public Object getValue() {
      return value;
    }

    public String getRegexPattern() {
      return regexPattern;
    }

    /**
     * @param actual a span name, log body, attribute key or attribute value as recorded: a String,
     *   Boolean, Long, Double or a list of those
     * @return whether it matches
     */
    public boolean matches(Object actual) {
      if (actual == null) {
        return false;
      }
      if (regexPattern != null) {
        return compiledRegex != null && actual instanceof String && compiledRegex.matcher((String) actual).find();
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

  /** Matches an attribute by key and value. */
  public static final class AttributeMatchConfig {
    private final MatchConfig key;
    private final MatchConfig attribute;

    public AttributeMatchConfig(MatchConfig key, MatchConfig attribute) {
      this.key = key;
      this.attribute = attribute;
    }

    public MatchConfig getKey() {
      return key;
    }

    public MatchConfig getAttribute() {
      return attribute;
    }
  }

  /** Matches a span event by name and attributes. */
  public static final class SpanEventMatchConfig {
    private final MatchConfig name;
    private final List<AttributeMatchConfig> attributes;

    public SpanEventMatchConfig(MatchConfig name, List<AttributeMatchConfig> attributes) {
      this.name = name;
      this.attributes = attributes != null ? attributes : Collections.<AttributeMatchConfig>emptyList();
    }

    public MatchConfig getName() {
      return name;
    }

    public List<AttributeMatchConfig> getAttributes() {
      return attributes;
    }
  }

  /** A span rule: the spans it matches and the ratio they are sampled at. */
  public static final class SpanSamplingConfig {
    private final MatchConfig name;
    private final List<AttributeMatchConfig> attributes;
    private final List<SpanEventMatchConfig> events;
    private final int samplingRatio;

    public SpanSamplingConfig(MatchConfig name, List<AttributeMatchConfig> attributes,
        List<SpanEventMatchConfig> events, int samplingRatio) {
      this.name = name;
      this.attributes = attributes != null ? attributes : Collections.<AttributeMatchConfig>emptyList();
      this.events = events != null ? events : Collections.<SpanEventMatchConfig>emptyList();
      this.samplingRatio = samplingRatio;
    }

    public MatchConfig getName() {
      return name;
    }

    public List<AttributeMatchConfig> getAttributes() {
      return attributes;
    }

    public List<SpanEventMatchConfig> getEvents() {
      return events;
    }

    public int getSamplingRatio() {
      return samplingRatio;
    }
  }

  /** A log rule: the records it matches and the ratio they are sampled at. */
  public static final class LogSamplingConfig {
    private final MatchConfig message;
    private final MatchConfig severityText;
    private final List<AttributeMatchConfig> attributes;
    private final int samplingRatio;

    public LogSamplingConfig(MatchConfig message, MatchConfig severityText, List<AttributeMatchConfig> attributes,
        int samplingRatio) {
      this.message = message;
      this.severityText = severityText;
      this.attributes = attributes != null ? attributes : Collections.<AttributeMatchConfig>emptyList();
      this.samplingRatio = samplingRatio;
    }

    public MatchConfig getMessage() {
      return message;
    }

    public MatchConfig getSeverityText() {
      return severityText;
    }

    public List<AttributeMatchConfig> getAttributes() {
      return attributes;
    }

    public int getSamplingRatio() {
      return samplingRatio;
    }
  }
}
