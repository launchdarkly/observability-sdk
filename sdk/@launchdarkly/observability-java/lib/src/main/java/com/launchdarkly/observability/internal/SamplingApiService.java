package com.launchdarkly.observability.internal;

import com.launchdarkly.observability.internal.sampling.SamplingConfig;
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
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Fetches sampling configuration from the LaunchDarkly backend via GraphQL.
 */
public final class SamplingApiService {

    private static final Logger log = Logger.getLogger(SamplingApiService.class.getName());
    /**
     * The sampling query as a compile-time constant. Go, .NET, Android and
     * Node keep the query in code too: a classpath resource can be dropped by
     * shaded jars built with minimize(), by ProGuard, and by some app-server
     * and OSGi classloaders, and reading it at runtime adds I/O on a server.
     */
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

    public SamplingApiService(GraphQLClient client) {
        this.client = client;
    }

    /**
     * Fetches the sampling configuration for the given SDK key.
     *
     * @param sdkKey the LaunchDarkly SDK key (used as organization_verbose_id)
     * @return the parsed sampling configuration, or null on error
     */
    public SamplingConfig getSamplingConfig(String sdkKey) {
        try {
            LDValue data = client.execute(QUERY, Collections.singletonMap("organization_verbose_id", sdkKey));
            if (data == null) {
                return null;
            }
            LDValue sampling = objectOrNull(data, "sampling");
            if (sampling == null) {
                return null;
            }
            return parseSamplingConfig(sampling);
        } catch (Exception e) {
            // A server that cannot reach the backend logs this on every
            // start, so one line; the stack trace is there at FINE.
            log.warning("Could not fetch the LaunchDarkly sampling config; exporting everything unsampled: " + e);
            log.log(Level.FINE, "Sampling config fetch failure", e);
            return null;
        }
    }

    /** Parses the {@code sampling} object of the GraphQL response. Visible for the shared scenario tests. */
    static SamplingConfig parseSamplingConfig(LDValue sampling) {
        List<SpanSamplingConfig> spans = new ArrayList<>();
        for (LDValue el : objects(sampling.get("spans"))) {
            spans.add(parseSpanConfig(el));
        }
        List<LogSamplingConfig> logs = new ArrayList<>();
        for (LDValue el : objects(sampling.get("logs"))) {
            logs.add(parseLogConfig(el));
        }
        return new SamplingConfig(spans, logs);
    }

    private static SpanSamplingConfig parseSpanConfig(LDValue obj) {
        List<SpanEventMatchConfig> events = new ArrayList<>();
        for (LDValue event : objects(obj.get("events"))) {
            events.add(new SpanEventMatchConfig(
                    parseMatchConfig(objectOrNull(event, "name")),
                    parseAttributeConfigs(event.get("attributes"))
            ));
        }
        return new SpanSamplingConfig(
                parseMatchConfig(objectOrNull(obj, "name")),
                parseAttributeConfigs(obj.get("attributes")),
                events,
                ratioOrDefault(obj));
    }

    private static LogSamplingConfig parseLogConfig(LDValue obj) {
        return new LogSamplingConfig(
                parseMatchConfig(objectOrNull(obj, "message")),
                parseMatchConfig(objectOrNull(obj, "severityText")),
                parseAttributeConfigs(obj.get("attributes")),
                ratioOrDefault(obj));
    }

    private static List<AttributeMatchConfig> parseAttributeConfigs(LDValue array) {
        List<AttributeMatchConfig> result = new ArrayList<>();
        for (LDValue obj : objects(array)) {
            result.add(new AttributeMatchConfig(
                    parseMatchConfig(objectOrNull(obj, "key")),
                    parseMatchConfig(objectOrNull(obj, "attribute"))
            ));
        }
        return result;
    }

    /**
     * GraphQL returns {@code null} for optional fields that are unset. One
     * null or mistyped field must not abort the whole payload, so lookups go
     * through these and treat anything unexpected like absent.
     */
    private static LDValue objectOrNull(LDValue parent, String key) {
        LDValue el = parent.get(key);
        return el.getType() == LDValueType.OBJECT ? el : null;
    }

    /** The object elements of an array; empty for null or a non-array. */
    private static List<LDValue> objects(LDValue array) {
        if (array.getType() != LDValueType.ARRAY) {
            return Collections.emptyList();
        }
        List<LDValue> result = new ArrayList<>();
        for (LDValue el : array.values()) {
            if (el.getType() == LDValueType.OBJECT) {
                result.add(el);
            }
        }
        return result;
    }

    private static int ratioOrDefault(LDValue obj) {
        LDValue el = obj.get("samplingRatio");
        return el.isNumber() ? el.intValue() : 1;
    }

    /**
     * Like Go, an exact value takes precedence over a regex, and an empty
     * regex counts as unset. A config with neither is no filter.
     */
    private static MatchConfig parseMatchConfig(LDValue obj) {
        if (obj == null) return null;
        LDValue match = obj.get("matchValue");
        if (!match.isNull()) {
            return MatchConfig.ofValue(matchValue(match));
        }
        LDValue regex = obj.get("regexValue");
        if (regex.isString() && !regex.stringValue().isEmpty()) {
            return MatchConfig.ofRegex(regex.stringValue());
        }
        return null;
    }

    /**
     * Keeps the JSON type of a match value so a number matches a numeric
     * attribute and a boolean a boolean, as in the Go and .NET SDKs. An array
     * or object is kept as is and never matches, instead of throwing and
     * losing the whole sampling payload.
     */
    private static Object matchValue(LDValue el) {
        switch (el.getType()) {
            case BOOLEAN:
                return el.booleanValue();
            case NUMBER:
                return el.doubleValue();
            case STRING:
                return el.stringValue();
            default:
                return el;
        }
    }
}
