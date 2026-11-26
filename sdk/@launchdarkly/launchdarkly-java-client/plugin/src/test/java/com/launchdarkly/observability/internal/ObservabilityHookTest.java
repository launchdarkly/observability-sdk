package com.launchdarkly.observability.internal;

import com.launchdarkly.sdk.EvaluationDetail;
import com.launchdarkly.sdk.EvaluationReason;
import com.launchdarkly.sdk.LDContext;
import com.launchdarkly.sdk.LDValue;
import com.launchdarkly.sdk.client.integrations.EvaluationSeriesContext;
import com.launchdarkly.sdk.client.integrations.IdentifySeriesContext;
import com.launchdarkly.sdk.client.integrations.IdentifySeriesResult;
import com.launchdarkly.sdk.client.integrations.TrackSeriesContext;
import com.launchdarkly.observability.internal.sampling.CustomSampler;
import com.launchdarkly.observability.internal.sampling.SamplingConfig;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.SpanId;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceId;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.sdk.testing.trace.TestSpanData;
import io.opentelemetry.sdk.trace.data.EventData;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.data.StatusData;

import org.junit.After;
import org.junit.Test;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class ObservabilityHookTest {
  private static final LDContext USER = LDContext.create("u");
  private static final EvaluationSeriesContext EVALUATION =
      new EvaluationSeriesContext("LDClient.boolVariation", "flag", USER, LDValue.of(false), null);

  private final TestTelemetry telemetry = new TestTelemetry();
  private final AtomicReference<ObservabilityService> current = new AtomicReference<>();
  private final ObservabilityHook hook = new ObservabilityHook(current::get);

  @After
  public void tearDown() {
    ObservabilityService service = current.get();
    if (service != null) {
      service.shutdown();
    }
  }

  private ObservabilityService start() {
    ObservabilityService service = telemetry.service(TestTelemetry.quietOptions().build());
    current.set(service);
    return service;
  }

  @Test
  public void withNoServiceEveryStageDoesNothing() {
    Map<String, Object> data = Collections.emptyMap();
    assertSame(data, hook.beforeEvaluation(EVALUATION, data));
    assertSame(data, hook.afterEvaluation(EVALUATION, data, EvaluationDetail.fromValue(LDValue.of(true), 0,
        EvaluationReason.off())));
    assertSame(data, hook.afterIdentify(new IdentifySeriesContext(USER, Duration.ZERO, null), data,
        new IdentifySeriesResult(IdentifySeriesResult.IdentifySeriesStatus.COMPLETED)));
    hook.afterTrack(new TrackSeriesContext("t", USER, LDValue.ofNull(), null, null));
  }

  @Test
  public void anEvaluationStartedWithoutAServiceIsNotEnded() {
    Map<String, Object> before = hook.beforeEvaluation(EVALUATION, Collections.<String, Object>emptyMap());
    ObservabilityService service = start();
    hook.afterEvaluation(EVALUATION, before, EvaluationDetail.fromValue(LDValue.of(true), 0, EvaluationReason.off()));
    assertThat(telemetry.finishedSpans(service), empty());
  }

  @Test
  public void aServiceGoneByTheEndLeavesTheSpanOpen() {
    start();
    Map<String, Object> before = hook.beforeEvaluation(EVALUATION, Collections.<String, Object>emptyMap());
    ObservabilityService service = current.getAndSet(null);
    hook.afterEvaluation(EVALUATION, before, EvaluationDetail.fromValue(LDValue.of(true), 0, EvaluationReason.off()));
    assertThat(telemetry.finishedSpans(service), empty());
    service.shutdown();
  }

  @Test
  public void anEvaluationWithoutAReasonHasNoExperimentAttribute() {
    ObservabilityService service = start();
    Map<String, Object> before = hook.beforeEvaluation(EVALUATION, Collections.<String, Object>emptyMap());
    hook.afterEvaluation(EVALUATION, before, EvaluationDetail.fromValue(LDValue.of(true), 1, null));
    EventData event = telemetry.spansNamed(service, "evaluation").get(0).getEvents().get(0);
    assertFalse(event.getAttributes().asMap().containsKey(ObservabilityService.FEATURE_FLAG_IN_EXPERIMENT));
    assertEquals(Long.valueOf(1), event.getAttributes().get(ObservabilityService.FEATURE_FLAG_VARIATION_INDEX));
  }

  @Test
  public void aFailedIdentifyIsNotLogged() {
    ObservabilityService service = start();
    hook.afterIdentify(new IdentifySeriesContext(USER, Duration.ZERO, null), Collections.<String, Object>emptyMap(),
        new IdentifySeriesResult(IdentifySeriesResult.IdentifySeriesStatus.ERROR));
    assertThat(telemetry.finishedLogs(service), empty());
  }

  @Test
  public void globalAttributesAreAddedToSpansAndLogsOnlyWhenSet() {
    ObservabilityService service = start();
    GlobalAttributes.set(Attributes.builder().put("own", "global").build());
    try {
      service.startSpan("s", Attributes.builder().put("own", "span").build()).end();
      service.recordLog("l", io.opentelemetry.api.logs.Severity.INFO, Attributes.builder().put("own", "log").build(),
          null);
      assertEquals("span", telemetry.finishedSpans(service).get(0).getAttributes().get(AttributeKey.stringKey("own")));
      assertEquals("log", telemetry.finishedLogs(service).get(0).getAttributes().get(AttributeKey.stringKey("own")));
    } finally {
      GlobalAttributes.clearForTest();
    }
  }

  // -- sampler branches the shared scenarios do not reach --

  private static SpanData span(Attributes attributes, EventData... events) {
    return TestSpanData.builder()
        .setName("s")
        .setKind(SpanKind.INTERNAL)
        .setSpanContext(SpanContext.create(TraceId.fromLongs(1, 1), SpanId.fromLong(1), TraceFlags.getSampled(),
            TraceState.getDefault()))
        .setStatus(StatusData.unset())
        .setHasEnded(true)
        .setStartEpochNanos(0)
        .setEndEpochNanos(1)
        .setAttributes(attributes)
        .setEvents(Arrays.asList(events))
        .setTotalRecordedEvents(events.length)
        .build();
  }

  @Test
  public void spanRulesNeedEveryEventAndAttributeRuleToMatch() {
    CustomSampler sampler = new CustomSampler(ratio -> false);
    SamplingConfig.AttributeMatchConfig attribute = new SamplingConfig.AttributeMatchConfig(
        SamplingConfig.MatchConfig.ofValue("k"), SamplingConfig.MatchConfig.ofValue("v"));
    SamplingConfig.SpanEventMatchConfig event = new SamplingConfig.SpanEventMatchConfig(
        SamplingConfig.MatchConfig.ofValue("e"), Collections.singletonList(attribute));
    sampler.setConfig(new SamplingConfig(Collections.singletonList(new SamplingConfig.SpanSamplingConfig(null,
        Collections.singletonList(attribute), Collections.singletonList(event), 2)), null));

    Attributes matching = Attributes.builder().put("other", "x").put("k", "v").build();
    EventData matchingEvent = EventData.create(0, "e", matching);
    assertFalse("all match", sampler.sampleSpan(span(matching, EventData.create(0, "x", matching), matchingEvent))
        .isSampled());
    assertTrue("no event", sampler.sampleSpan(span(matching)).isSampled());
    assertTrue("event attribute differs", sampler.sampleSpan(span(matching,
        EventData.create(0, "e", Attributes.builder().put("k", "w").build()))).isSampled());
    assertTrue("span attribute differs", sampler.sampleSpan(span(Attributes.builder().put("k", "w").build(),
        matchingEvent)).isSampled());
  }

  @Test
  public void logRulesCheckSeverityThenMessageThenAttributes() {
    CustomSampler sampler = new CustomSampler(ratio -> false);
    sampler.setConfig(new SamplingConfig(null, Collections.singletonList(new SamplingConfig.LogSamplingConfig(
        SamplingConfig.MatchConfig.ofValue("m"), SamplingConfig.MatchConfig.ofValue("ERROR"), null, 2))));
    assertFalse(sampler.sampleLog(log("m", "ERROR")).isSampled());
    assertTrue(sampler.sampleLog(log("m", "INFO")).isSampled());
    assertTrue(sampler.sampleLog(log("other", "ERROR")).isSampled());
    assertTrue(sampler.sampleLog(log(null, "ERROR")).isSampled());
  }

  private static io.opentelemetry.sdk.logs.data.LogRecordData log(String body, String severityText) {
    io.opentelemetry.sdk.testing.logs.TestLogRecordData.Builder builder =
        io.opentelemetry.sdk.testing.logs.TestLogRecordData.builder().setSeverityText(severityText);
    if (body != null) {
      builder.setBodyValue(io.opentelemetry.api.common.Value.of(body));
    }
    return builder.build();
  }
}
