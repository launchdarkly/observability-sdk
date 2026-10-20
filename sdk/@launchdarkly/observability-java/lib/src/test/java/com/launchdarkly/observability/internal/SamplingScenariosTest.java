package com.launchdarkly.observability.internal;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.launchdarkly.observability.internal.sampling.CustomSampler;
import com.launchdarkly.observability.internal.sampling.SamplingConfig;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.common.Value;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.sdk.logs.data.LogRecordData;
import io.opentelemetry.sdk.testing.logs.TestLogRecordData;
import io.opentelemetry.sdk.testing.trace.TestSpanData;
import io.opentelemetry.sdk.trace.data.EventData;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.data.StatusData;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runs the sampling scenarios shared with the Go, .NET, Python and Node SDKs.
 *
 * <p>The fixtures in {@code src/test/resources/sampling} are copies of
 * {@code go/internal/otel/*-test-scenarios.json}. Each scenario's
 * {@code samplingConfig} goes through the same parser as the backend
 * response, so parsing and matching are both covered.</p>
 */
class SamplingScenariosTest {

    @TestFactory
    List<DynamicTest> spanScenarios() throws IOException {
        List<DynamicTest> tests = new ArrayList<>();
        for (JsonElement el : loadScenarios("sampling/span-test-scenarios.json")) {
            JsonObject scenario = el.getAsJsonObject();
            SamplingConfig config = SamplingApiService.parseSamplingConfig(scenario.getAsJsonObject("samplingConfig"));
            SpanData span = span(scenario.getAsJsonObject("inputSpan"));
            for (JsonElement c : scenario.getAsJsonArray("samplerFunctionCases")) {
                JsonObject samplerCase = c.getAsJsonObject();
                tests.add(DynamicTest.dynamicTest(
                        scenario.get("description").getAsString() + " [" + samplerCase.get("type").getAsString() + "]",
                        () -> {
                            CustomSampler sampler = sampler(samplerCase);
                            sampler.setConfig(config);
                            assertResult(samplerCase.getAsJsonObject("expected_result"), sampler.sampleSpan(span));
                        }));
            }
        }
        assertTrue(tests.size() > 0, "no span scenarios loaded");
        return tests;
    }

    @TestFactory
    List<DynamicTest> logScenarios() throws IOException {
        List<DynamicTest> tests = new ArrayList<>();
        for (JsonElement el : loadScenarios("sampling/log-test-scenarios.json")) {
            JsonObject scenario = el.getAsJsonObject();
            SamplingConfig config = SamplingApiService.parseSamplingConfig(scenario.getAsJsonObject("samplingConfig"));
            LogRecordData log = log(scenario.getAsJsonObject("inputLog"));
            for (JsonElement c : scenario.getAsJsonArray("samplerFunctionCases")) {
                JsonObject samplerCase = c.getAsJsonObject();
                tests.add(DynamicTest.dynamicTest(
                        scenario.get("description").getAsString() + " [" + samplerCase.get("type").getAsString() + "]",
                        () -> {
                            CustomSampler sampler = sampler(samplerCase);
                            sampler.setConfig(config);
                            assertResult(samplerCase.getAsJsonObject("expected_result"), sampler.sampleLog(log));
                        }));
            }
        }
        assertTrue(tests.size() > 0, "no log scenarios loaded");
        return tests;
    }

    private static CustomSampler sampler(JsonObject samplerCase) {
        String type = samplerCase.get("type").getAsString();
        switch (type) {
            case "always":
                return new CustomSampler(ratio -> true);
            case "never":
                return new CustomSampler(ratio -> false);
            default:
                throw new IllegalArgumentException("unknown sampler type " + type);
        }
    }

    private static void assertResult(JsonObject expected, CustomSampler.SamplingResult actual) {
        assertEquals(expected.get("sample").getAsBoolean(), actual.isSampled(), "sample");
        JsonElement expectedAttributes = expected.get("attributes");
        if (expectedAttributes == null || expectedAttributes.isJsonNull()) {
            assertNull(actual.getAttributes(), "attributes");
            return;
        }
        assertNotNull(actual.getAttributes(), "attributes");
        assertEquals(attributes(expectedAttributes.getAsJsonObject()), actual.getAttributes(), "attributes");
    }

    private static SpanData span(JsonObject input) {
        List<EventData> events = new ArrayList<>();
        JsonElement eventsJson = input.get("events");
        if (eventsJson != null && eventsJson.isJsonArray()) {
            for (JsonElement e : eventsJson.getAsJsonArray()) {
                JsonObject event = e.getAsJsonObject();
                events.add(EventData.create(0, event.get("name").getAsString(), attributes(event.getAsJsonObject("attributes"))));
            }
        }
        return TestSpanData.builder()
                .setName(input.get("name").getAsString())
                .setKind(SpanKind.INTERNAL)
                .setSpanContext(SpanContext.create(
                        "0123456789abcdef0123456789abcdef", "0123456789abcdef",
                        TraceFlags.getSampled(), TraceState.getDefault()))
                .setStatus(StatusData.unset())
                .setHasEnded(true)
                .setStartEpochNanos(0)
                .setEndEpochNanos(1)
                .setAttributes(attributes(input.getAsJsonObject("attributes")))
                .setEvents(events)
                .setTotalRecordedEvents(events.size())
                .build();
    }

    private static LogRecordData log(JsonObject input) {
        TestLogRecordData.Builder builder = TestLogRecordData.builder()
                .setAttributes(attributes(input.getAsJsonObject("attributes")));
        JsonElement message = input.get("message");
        if (message != null && !message.isJsonNull()) {
            builder.setBodyValue(Value.of(message.getAsString()));
        }
        JsonElement severityText = input.get("severityText");
        if (severityText != null && !severityText.isJsonNull()) {
            builder.setSeverityText(severityText.getAsString());
        }
        return builder.build();
    }

    /** JSON strings, integers, decimals and booleans become the matching OTel attribute types. */
    private static Attributes attributes(JsonObject json) {
        if (json == null) {
            return Attributes.empty();
        }
        AttributesBuilder builder = Attributes.builder();
        for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
            JsonPrimitive value = entry.getValue().getAsJsonPrimitive();
            if (value.isBoolean()) {
                builder.put(AttributeKey.booleanKey(entry.getKey()), value.getAsBoolean());
            } else if (value.isNumber()) {
                double number = value.getAsDouble();
                if (number == Math.rint(number) && !value.getAsString().contains(".")) {
                    builder.put(AttributeKey.longKey(entry.getKey()), value.getAsLong());
                } else {
                    builder.put(AttributeKey.doubleKey(entry.getKey()), number);
                }
            } else {
                builder.put(AttributeKey.stringKey(entry.getKey()), value.getAsString());
            }
        }
        return builder.build();
    }

    private static JsonElement[] loadScenarios(String resource) throws IOException {
        try (InputStream in = SamplingScenariosTest.class.getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(in, "missing test resource " + resource);
            List<JsonElement> scenarios = new ArrayList<>();
            JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8))
                    .getAsJsonArray().forEach(scenarios::add);
            return scenarios.toArray(new JsonElement[0]);
        }
    }
}
