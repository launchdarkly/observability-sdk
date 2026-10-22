package com.launchdarkly.observability.internal;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.launchdarkly.observability.internal.sampling.SamplingConfig;
import com.launchdarkly.observability.internal.sampling.SamplingConfig.MatchConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SamplingConfigParsingTest {

    @Test
    void matchValuesKeepTheirJsonType() {
        SamplingConfig config = parse("{\"spans\":[{\"attributes\":["
                + "{\"key\":{\"matchValue\":\"status\"},\"attribute\":{\"matchValue\":500}},"
                + "{\"key\":{\"matchValue\":\"retry\"},\"attribute\":{\"matchValue\":true}},"
                + "{\"key\":{\"matchValue\":\"route\"},\"attribute\":{\"matchValue\":\"/health\"}}"
                + "],\"samplingRatio\":10}]}");

        SamplingConfig.SpanSamplingConfig rule = config.getSpans().get(0);
        assertEquals(500.0, rule.getAttributes().get(0).getAttribute().getValue());
        assertEquals(true, rule.getAttributes().get(1).getAttribute().getValue());
        assertEquals("/health", rule.getAttributes().get(2).getAttribute().getValue());
        assertTrue(rule.getAttributes().get(0).getAttribute().matches(500L));
    }

    @Test
    void unsupportedMatchValueNeverMatchesAndKeepsTheRestOfThePayload() {
        SamplingConfig config = parse("{\"spans\":["
                + "{\"name\":{\"matchValue\":[\"a\",\"b\"]},\"samplingRatio\":0},"
                + "{\"name\":{\"matchValue\":\"health\"},\"samplingRatio\":0}"
                + "],\"logs\":[{\"message\":{\"matchValue\":{\"nested\":1}},\"samplingRatio\":0}]}");

        assertEquals(2, config.getSpans().size());
        assertFalse(config.getSpans().get(0).getName().matches("a"));
        assertTrue(config.getSpans().get(1).getName().matches("health"));
        assertEquals(1, config.getLogs().size());
    }

    @Test
    void exactValueWinsOverRegexAndEmptyRegexIsNoFilter() {
        SamplingConfig config = parse("{\"spans\":["
                + "{\"name\":{\"matchValue\":\"exact\",\"regexValue\":\".*\"},\"samplingRatio\":0},"
                + "{\"name\":{\"matchValue\":null,\"regexValue\":\"\"},\"samplingRatio\":0}"
                + "]}");

        MatchConfig both = config.getSpans().get(0).getName();
        assertFalse(both.isRegex());
        assertFalse(both.matches("other"));
        assertNull(config.getSpans().get(1).getName(), "an empty regex is treated as unset");
    }

    private static SamplingConfig parse(String sampling) {
        JsonObject json = JsonParser.parseString(sampling).getAsJsonObject();
        return SamplingApiService.parseSamplingConfig(json);
    }
}
