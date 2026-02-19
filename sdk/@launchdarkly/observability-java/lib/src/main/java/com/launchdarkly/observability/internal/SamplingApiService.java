package com.launchdarkly.observability.internal;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
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
    private static final String QUERY_FILE = "graphql/GetSamplingConfigQuery.graphql";

    private final GraphQLClient client;
    private final String query;

    public SamplingApiService(GraphQLClient client) {
        this.client = client;
        this.query = GraphQLClient.loadQuery(QUERY_FILE);
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
            JsonElement data = client.execute(query, variables);
            if (data == null || !data.isJsonObject()) {
                return null;
            }
            JsonObject sampling = data.getAsJsonObject().getAsJsonObject("sampling");
            if (sampling == null) {
                return null;
            }
            return parseSamplingConfig(sampling);
        } catch (Exception e) {
            log.log(Level.WARNING, "Failed to fetch sampling config", e);
            return null;
        }
    }

    private SamplingConfig parseSamplingConfig(JsonObject sampling) {
        List<SpanSamplingConfig> spans = new ArrayList<>();
        List<LogSamplingConfig> logs = new ArrayList<>();

        JsonArray spansArray = sampling.getAsJsonArray("spans");
        if (spansArray != null) {
            for (JsonElement el : spansArray) {
                spans.add(parseSpanConfig(el.getAsJsonObject()));
            }
        }

        JsonArray logsArray = sampling.getAsJsonArray("logs");
        if (logsArray != null) {
            for (JsonElement el : logsArray) {
                logs.add(parseLogConfig(el.getAsJsonObject()));
            }
        }

        return new SamplingConfig(spans, logs);
    }

    private SpanSamplingConfig parseSpanConfig(JsonObject obj) {
        MatchConfig name = parseMatchConfig(obj.getAsJsonObject("name"));
        List<AttributeMatchConfig> attributes = parseAttributeConfigs(obj.getAsJsonArray("attributes"));
        List<SpanEventMatchConfig> events = new ArrayList<>();

        JsonArray eventsArray = obj.getAsJsonArray("events");
        if (eventsArray != null) {
            for (JsonElement el : eventsArray) {
                JsonObject eventObj = el.getAsJsonObject();
                events.add(new SpanEventMatchConfig(
                        parseMatchConfig(eventObj.getAsJsonObject("name")),
                        parseAttributeConfigs(eventObj.getAsJsonArray("attributes"))
                ));
            }
        }

        int ratio = obj.has("samplingRatio") ? obj.get("samplingRatio").getAsInt() : 1;
        return new SpanSamplingConfig(name, attributes, events, ratio);
    }

    private LogSamplingConfig parseLogConfig(JsonObject obj) {
        MatchConfig message = parseMatchConfig(obj.getAsJsonObject("message"));
        MatchConfig severityText = parseMatchConfig(obj.getAsJsonObject("severityText"));
        List<AttributeMatchConfig> attributes = parseAttributeConfigs(obj.getAsJsonArray("attributes"));
        int ratio = obj.has("samplingRatio") ? obj.get("samplingRatio").getAsInt() : 1;
        return new LogSamplingConfig(message, severityText, attributes, ratio);
    }

    private List<AttributeMatchConfig> parseAttributeConfigs(JsonArray array) {
        if (array == null) return Collections.emptyList();
        List<AttributeMatchConfig> result = new ArrayList<>();
        for (JsonElement el : array) {
            JsonObject obj = el.getAsJsonObject();
            result.add(new AttributeMatchConfig(
                    parseMatchConfig(obj.getAsJsonObject("key")),
                    parseMatchConfig(obj.getAsJsonObject("attribute"))
            ));
        }
        return result;
    }

    private MatchConfig parseMatchConfig(JsonObject obj) {
        if (obj == null) return null;
        if (obj.has("regexValue") && !obj.get("regexValue").isJsonNull()) {
            return MatchConfig.ofRegex(obj.get("regexValue").getAsString());
        }
        if (obj.has("matchValue") && !obj.get("matchValue").isJsonNull()) {
            return MatchConfig.ofValue(obj.get("matchValue").getAsString());
        }
        return null;
    }
}
