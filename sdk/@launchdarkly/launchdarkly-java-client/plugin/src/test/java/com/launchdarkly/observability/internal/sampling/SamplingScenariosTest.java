package com.launchdarkly.observability.internal.sampling;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.launchdarkly.logging.LDLogger;
import com.launchdarkly.sdk.LDValue;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.common.Value;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.SpanId;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceId;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.sdk.logs.data.LogRecordData;
import io.opentelemetry.sdk.testing.logs.TestLogRecordData;
import io.opentelemetry.sdk.testing.trace.TestSpanData;
import io.opentelemetry.sdk.trace.data.EventData;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.data.StatusData;

import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Runs the sampling scenarios shared by the Go, .NET, Python, Node and server-side Java SDKs. The
 * fixtures are copies of {@code go/internal/otel/*-test-scenarios.json} in observability-sdk. Each
 * scenario's configuration goes through the same parser as the backend's response, so parsing and
 * matching are both covered.
 */
public class SamplingScenariosTest {
  @Test
  public void spanScenarios() throws IOException {
    List<String> failures = new ArrayList<>();
    int cases = 0;
    for (JsonElement element : load("sampling/span-test-scenarios.json")) {
      JsonObject scenario = element.getAsJsonObject();
      SamplingConfig config = parse(scenario);
      SpanData span = span(scenario.getAsJsonObject("inputSpan"));
      for (JsonElement c : scenario.getAsJsonArray("samplerFunctionCases")) {
        cases++;
        CustomSampler sampler = sampler(c.getAsJsonObject());
        sampler.setConfig(config);
        check(failures, scenario, c.getAsJsonObject(), sampler.sampleSpan(span));
      }
    }
    assertTrue("no span scenarios", cases > 0);
    assertEquals(String.join("\n", failures), 0, failures.size());
  }

  @Test
  public void logScenarios() throws IOException {
    List<String> failures = new ArrayList<>();
    int cases = 0;
    for (JsonElement element : load("sampling/log-test-scenarios.json")) {
      JsonObject scenario = element.getAsJsonObject();
      SamplingConfig config = parse(scenario);
      LogRecordData log = log(scenario.getAsJsonObject("inputLog"));
      for (JsonElement c : scenario.getAsJsonArray("samplerFunctionCases")) {
        cases++;
        CustomSampler sampler = sampler(c.getAsJsonObject());
        sampler.setConfig(config);
        check(failures, scenario, c.getAsJsonObject(), sampler.sampleLog(log));
      }
    }
    assertTrue("no log scenarios", cases > 0);
    assertEquals(String.join("\n", failures), 0, failures.size());
  }

  private static SamplingConfig parse(JsonObject scenario) {
    return SamplingApiService.parseSamplingConfig(LDValue.parse(scenario.get("samplingConfig").toString()),
        LDLogger.none());
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

  private static void check(List<String> failures, JsonObject scenario, JsonObject samplerCase,
      CustomSampler.SamplingResult actual) {
    JsonObject expected = samplerCase.getAsJsonObject("expected_result");
    JsonElement expectedAttributes = expected.get("attributes");
    Attributes wanted = expectedAttributes == null || expectedAttributes.isJsonNull()
        ? null : attributes(expectedAttributes.getAsJsonObject());
    if (expected.get("sample").getAsBoolean() != actual.isSampled()
        || !Objects.equals(wanted, actual.getAttributes())) {
      failures.add(scenario.get("description").getAsString() + " [" + samplerCase.get("type").getAsString()
          + "]: expected sample=" + expected.get("sample") + " attributes=" + wanted + ", got sample="
          + actual.isSampled() + " attributes=" + actual.getAttributes());
    }
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
        .setSpanContext(SpanContext.create(TraceId.fromLongs(1, 1), SpanId.fromLong(1), TraceFlags.getSampled(),
            TraceState.getDefault()))
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

  /** JSON strings, integers, decimals and booleans become the matching attribute types. */
  static Attributes attributes(JsonObject json) {
    if (json == null) {
      return Attributes.empty();
    }
    AttributesBuilder builder = Attributes.builder();
    for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
      JsonPrimitive value = entry.getValue().getAsJsonPrimitive();
      if (value.isBoolean()) {
        builder.put(AttributeKey.booleanKey(entry.getKey()), value.getAsBoolean());
      } else if (value.isNumber()) {
        if (value.getAsString().contains(".")) {
          builder.put(AttributeKey.doubleKey(entry.getKey()), value.getAsDouble());
        } else {
          builder.put(AttributeKey.longKey(entry.getKey()), value.getAsLong());
        }
      } else {
        builder.put(AttributeKey.stringKey(entry.getKey()), value.getAsString());
      }
    }
    return builder.build();
  }

  private static List<JsonElement> load(String resource) throws IOException {
    try (InputStream in = SamplingScenariosTest.class.getClassLoader().getResourceAsStream(resource)) {
      assertNotNull("missing test resource " + resource, in);
      List<JsonElement> scenarios = new ArrayList<>();
      JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonArray().forEach(scenarios::add);
      return scenarios;
    }
  }
}
