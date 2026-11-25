package com.launchdarkly.observability.internal.sampling;

import com.launchdarkly.logging.LDLogger;
import com.launchdarkly.observability.internal.sampling.SamplingConfig.AttributeMatchConfig;
import com.launchdarkly.observability.internal.sampling.SamplingConfig.LogSamplingConfig;
import com.launchdarkly.observability.internal.sampling.SamplingConfig.MatchConfig;
import com.launchdarkly.observability.internal.sampling.SamplingConfig.SpanEventMatchConfig;
import com.launchdarkly.observability.internal.sampling.SamplingConfig.SpanSamplingConfig;
import com.launchdarkly.sdk.LDValue;
import com.launchdarkly.sdk.LDValueType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Fetches the sampling rules for a project from LaunchDarkly over GraphQL, and parses them. Ported
 * from the server-side Java plugin, which keeps the query in code as the Go, .NET, Android and Node
 * SDKs do.
 */
public final class SamplingApiService {
  static final String QUERY = ""
      + "fragment MatchParts on MatchConfig {\n"
      + "  regexValue\n"
      + "  matchValue\n"
      + "}\n"
      + "\n"
      + "query GetSamplingConfig($organization_verbose_id: String!) {\n"
      + "  sampling(organization_verbose_id: $organization_verbose_id) {\n"
      + "    spans {\n"
      + "      name {\n"
      + "        ...MatchParts\n"
      + "      }\n"
      + "      attributes {\n"
      + "        key {\n"
      + "          ...MatchParts\n"
      + "        }\n"
      + "        attribute {\n"
      + "          ...MatchParts\n"
      + "        }\n"
      + "      }\n"
      + "      events {\n"
      + "        name {\n"
      + "          ...MatchParts\n"
      + "        }\n"
      + "        attributes {\n"
      + "          key {\n"
      + "            ...MatchParts\n"
      + "          }\n"
      + "          attribute {\n"
      + "            ...MatchParts\n"
      + "          }\n"
      + "        }\n"
      + "      }\n"
      + "      samplingRatio\n"
      + "    }\n"
      + "    logs {\n"
      + "      message {\n"
      + "        ...MatchParts\n"
      + "      }\n"
      + "      severityText {\n"
      + "        ...MatchParts\n"
      + "      }\n"
      + "      attributes {\n"
      + "        key {\n"
      + "          ...MatchParts\n"
      + "        }\n"
      + "        attribute {\n"
      + "          ...MatchParts\n"
      + "        }\n"
      + "      }\n"
      + "      samplingRatio\n"
      + "    }\n"
      + "  }\n"
      + "}\n";

  private final GraphQLClient client;
  private final LDLogger logger;

  public SamplingApiService(GraphQLClient client, LDLogger logger) {
    this.client = client;
    this.logger = logger;
  }

  /**
   * Fetches the sampling rules for the project the credential belongs to.
   *
   * @param credential the mobile key or client-side ID, which the backend takes as the project id
   * @return the rules, or null if they could not be fetched or parsed
   */
  public SamplingConfig getSamplingConfig(String credential) {
    try {
      LDValue data = client.execute(QUERY, Collections.singletonMap("organization_verbose_id", credential));
      LDValue sampling = data == null ? null : objectOrNull(data, "sampling");
      return sampling == null ? null : parseSamplingConfig(sampling, logger);
    } catch (Exception e) {
      logger.warn("Could not fetch the LaunchDarkly sampling configuration, so nothing is sampled: {}",
          e.toString());
      return null;
    }
  }

  /** Parses the {@code sampling} object of the GraphQL response. */
  static SamplingConfig parseSamplingConfig(LDValue sampling, LDLogger logger) {
    List<SpanSamplingConfig> spans = new ArrayList<>();
    for (LDValue element : objects(sampling.get("spans"))) {
      spans.add(parseSpanConfig(element, logger));
    }
    List<LogSamplingConfig> logs = new ArrayList<>();
    for (LDValue element : objects(sampling.get("logs"))) {
      logs.add(new LogSamplingConfig(
          parseMatchConfig(objectOrNull(element, "message"), logger),
          parseMatchConfig(objectOrNull(element, "severityText"), logger),
          parseAttributeConfigs(element.get("attributes"), logger),
          ratioOrDefault(element)));
    }
    return new SamplingConfig(spans, logs);
  }

  private static SpanSamplingConfig parseSpanConfig(LDValue object, LDLogger logger) {
    List<SpanEventMatchConfig> events = new ArrayList<>();
    for (LDValue event : objects(object.get("events"))) {
      events.add(new SpanEventMatchConfig(parseMatchConfig(objectOrNull(event, "name"), logger),
          parseAttributeConfigs(event.get("attributes"), logger)));
    }
    return new SpanSamplingConfig(parseMatchConfig(objectOrNull(object, "name"), logger),
        parseAttributeConfigs(object.get("attributes"), logger), events, ratioOrDefault(object));
  }

  private static List<AttributeMatchConfig> parseAttributeConfigs(LDValue array, LDLogger logger) {
    List<AttributeMatchConfig> result = new ArrayList<>();
    for (LDValue object : objects(array)) {
      result.add(new AttributeMatchConfig(parseMatchConfig(objectOrNull(object, "key"), logger),
          parseMatchConfig(objectOrNull(object, "attribute"), logger)));
    }
    return result;
  }

  /**
   * GraphQL sends null for an unset optional field. One null or mistyped field must not lose the
   * whole configuration, so anything unexpected counts as absent.
   */
  private static LDValue objectOrNull(LDValue parent, String key) {
    LDValue element = parent.get(key);
    return element.getType() == LDValueType.OBJECT ? element : null;
  }

  /** The object elements of an array; none for null or a non-array. */
  private static List<LDValue> objects(LDValue array) {
    List<LDValue> result = new ArrayList<>();
    if (array.getType() == LDValueType.ARRAY) {
      for (LDValue element : array.values()) {
        if (element.getType() == LDValueType.OBJECT) {
          result.add(element);
        }
      }
    }
    return result;
  }

  private static int ratioOrDefault(LDValue object) {
    LDValue ratio = object.get("samplingRatio");
    return ratio.isNumber() ? ratio.intValue() : 1;
  }

  /**
   * As in Go, an exact value takes precedence over a regex, and an empty regex counts as unset. A
   * config with neither is no filter.
   */
  private static MatchConfig parseMatchConfig(LDValue object, LDLogger logger) {
    if (object == null) {
      return null;
    }
    LDValue match = object.get("matchValue");
    if (!match.isNull()) {
      return MatchConfig.ofValue(matchValue(match));
    }
    LDValue regex = object.get("regexValue");
    if (regex.isString() && !regex.stringValue().isEmpty()) {
      MatchConfig config = MatchConfig.ofRegex(regex.stringValue());
      if (!config.isValid()) {
        logger.warn("Skipping a sampling rule whose regex does not compile: {}", regex.stringValue());
      }
      return config;
    }
    return null;
  }

  /**
   * Keeps the JSON type of a match value, so a number matches a numeric attribute and a boolean a
   * boolean, as in Go and .NET. An array or object is kept as it is and never matches.
   */
  private static Object matchValue(LDValue element) {
    switch (element.getType()) {
      case BOOLEAN:
        return element.booleanValue();
      case NUMBER:
        return element.doubleValue();
      case STRING:
        return element.stringValue();
      default:
        return element;
    }
  }
}
