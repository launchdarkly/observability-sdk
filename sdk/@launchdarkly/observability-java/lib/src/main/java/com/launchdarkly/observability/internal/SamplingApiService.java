package com.launchdarkly.observability.internal;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.launchdarkly.observability.internal.sampling.SamplingConfig;
import com.launchdarkly.observability.internal.sampling.SamplingConfig.AttributeMatchConfig;
import com.launchdarkly.observability.internal.sampling.SamplingConfig.LogSamplingConfig;
import com.launchdarkly.observability.internal.sampling.SamplingConfig.MatchConfig;
import com.launchdarkly.observability.internal.sampling.SamplingConfig.SpanEventMatchConfig;
import com.launchdarkly.observability.internal.sampling.SamplingConfig.SpanSamplingConfig;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
            Map<String, Object> variables = new HashMap<>();
            variables.put("organization_verbose_id", sdkKey);
            JsonElement data = client.execute(QUERY, variables);
            if (data == null || !data.isJsonObject()) {
                return null;
            }
            JsonObject sampling = objectOrNull(data.getAsJsonObject(), "sampling");
            if (sampling == null) {
                return null;
            }
            return parseSamplingConfig(sampling);
        } catch (Exception e) {
            log.log(Level.WARNING, "Failed to fetch sampling config", e);
            return null;
        }
    }

    /** Parses the {@code sampling} object of the GraphQL response. Visible for the shared scenario tests. */
    static SamplingConfig parseSamplingConfig(JsonObject sampling) {
        List<SpanSamplingConfig> spans = new ArrayList<>();
        List<LogSamplingConfig> logs = new ArrayList<>();

        JsonArray spansArray = arrayOrNull(sampling, "spans");
        if (spansArray != null) {
            for (JsonElement el : spansArray) {
                if (el.isJsonObject()) {
                    spans.add(parseSpanConfig(el.getAsJsonObject()));
                }
            }
        }

        JsonArray logsArray = arrayOrNull(sampling, "logs");
        if (logsArray != null) {
            for (JsonElement el : logsArray) {
                if (el.isJsonObject()) {
                    logs.add(parseLogConfig(el.getAsJsonObject()));
                }
            }
        }

        return new SamplingConfig(spans, logs);
    }

    private static SpanSamplingConfig parseSpanConfig(JsonObject obj) {
        MatchConfig name = parseMatchConfig(objectOrNull(obj, "name"));
        List<AttributeMatchConfig> attributes = parseAttributeConfigs(arrayOrNull(obj, "attributes"));
        List<SpanEventMatchConfig> events = new ArrayList<>();

        JsonArray eventsArray = arrayOrNull(obj, "events");
        if (eventsArray != null) {
            for (JsonElement el : eventsArray) {
                if (!el.isJsonObject()) {
                    continue;
                }
                JsonObject eventObj = el.getAsJsonObject();
                events.add(new SpanEventMatchConfig(
                        parseMatchConfig(objectOrNull(eventObj, "name")),
                        parseAttributeConfigs(arrayOrNull(eventObj, "attributes"))
                ));
            }
        }

        return new SpanSamplingConfig(name, attributes, events, ratioOrDefault(obj));
    }

    private static LogSamplingConfig parseLogConfig(JsonObject obj) {
        MatchConfig message = parseMatchConfig(objectOrNull(obj, "message"));
        MatchConfig severityText = parseMatchConfig(objectOrNull(obj, "severityText"));
        List<AttributeMatchConfig> attributes = parseAttributeConfigs(arrayOrNull(obj, "attributes"));
        return new LogSamplingConfig(message, severityText, attributes, ratioOrDefault(obj));
    }

    private static List<AttributeMatchConfig> parseAttributeConfigs(JsonArray array) {
        if (array == null) return Collections.emptyList();
        List<AttributeMatchConfig> result = new ArrayList<>();
        for (JsonElement el : array) {
            if (!el.isJsonObject()) {
                continue;
            }
            JsonObject obj = el.getAsJsonObject();
            result.add(new AttributeMatchConfig(
                    parseMatchConfig(objectOrNull(obj, "key")),
                    parseMatchConfig(objectOrNull(obj, "attribute"))
            ));
        }
        return result;
    }

    /**
     * GraphQL returns {@code null} for optional fields that are unset, and
     * Gson's {@code getAsJsonObject} / {@code getAsJsonArray} throw on
     * {@code JsonNull}. One null field must not abort the whole payload, so
     * every lookup goes through these and treats null like absent.
     */
    private static JsonObject objectOrNull(JsonObject parent, String key) {
        JsonElement el = parent.get(key);
        return el != null && el.isJsonObject() ? el.getAsJsonObject() : null;
    }

    private static JsonArray arrayOrNull(JsonObject parent, String key) {
        JsonElement el = parent.get(key);
        return el != null && el.isJsonArray() ? el.getAsJsonArray() : null;
    }

    private static int ratioOrDefault(JsonObject obj) {
        JsonElement el = obj.get("samplingRatio");
        if (el == null || !el.isJsonPrimitive() || !el.getAsJsonPrimitive().isNumber()) {
            return 1;
        }
        return el.getAsInt();
    }

    /**
     * Like Go, an exact value takes precedence over a regex, and an empty
     * regex counts as unset. A config with neither is no filter.
     */
    private static MatchConfig parseMatchConfig(JsonObject obj) {
        if (obj == null) return null;
        JsonElement match = obj.get("matchValue");
        if (match != null && !match.isJsonNull()) {
            return MatchConfig.ofValue(matchValue(match));
        }
        JsonElement regex = obj.get("regexValue");
        if (regex != null && regex.isJsonPrimitive() && !regex.getAsString().isEmpty()) {
            return MatchConfig.ofRegex(regex.getAsString());
        }
        return null;
    }

    /**
     * Keeps the JSON type of a match value so a number matches a numeric
     * attribute and a boolean a boolean, as in the Go and .NET SDKs. An array
     * or object is kept as is and never matches, instead of throwing and
     * losing the whole sampling payload.
     */
    private static Object matchValue(JsonElement el) {
        if (!el.isJsonPrimitive()) {
            return el;
        }
        JsonPrimitive primitive = el.getAsJsonPrimitive();
        if (primitive.isBoolean()) {
            return primitive.getAsBoolean();
        }
        if (primitive.isNumber()) {
            return primitive.getAsDouble();
        }
        return primitive.getAsString();
    }
}
