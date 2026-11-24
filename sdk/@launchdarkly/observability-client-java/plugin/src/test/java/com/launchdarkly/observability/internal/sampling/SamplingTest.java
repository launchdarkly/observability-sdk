package com.launchdarkly.observability.internal.sampling;

import com.launchdarkly.logging.LDLogger;
import com.launchdarkly.logging.LogCapture;
import com.launchdarkly.logging.Logs;
import com.launchdarkly.sdk.LDValue;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.logs.Severity;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.SpanId;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceId;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.logs.LogRecordProcessor;
import io.opentelemetry.sdk.logs.ReadWriteLogRecord;
import io.opentelemetry.sdk.logs.SdkLoggerProvider;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.testing.trace.TestSpanData;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.data.StatusData;

import org.junit.After;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPInputStream;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class SamplingTest {
  private final LogCapture logCapture = Logs.capture();
  private final LDLogger logger = LDLogger.withAdapter(logCapture, "test");
  private final MockWebServer server = new MockWebServer();

  @After
  public void tearDown() throws IOException {
    server.shutdown();
  }

  private static SpanData span(String name, long id, long parentId) {
    SpanContext parent = parentId == 0 ? SpanContext.getInvalid()
        : SpanContext.create(TraceId.fromLongs(1, 1), SpanId.fromLong(parentId), TraceFlags.getSampled(),
            TraceState.getDefault());
    return TestSpanData.builder()
        .setName(name)
        .setKind(SpanKind.INTERNAL)
        .setSpanContext(SpanContext.create(TraceId.fromLongs(1, 1), SpanId.fromLong(id), TraceFlags.getSampled(),
            TraceState.getDefault()))
        .setParentSpanContext(parent)
        .setStatus(StatusData.unset())
        .setHasEnded(true)
        .setStartEpochNanos(0)
        .setEndEpochNanos(1)
        .setAttributes(Attributes.builder().put("a", "b").build())
        .build();
  }

  private static SamplingConfig dropSpansNamed(String name) {
    return new SamplingConfig(Collections.singletonList(new SamplingConfig.SpanSamplingConfig(
        SamplingConfig.MatchConfig.ofValue(name), null, null, 0)), null);
  }

  // -- the trace exporter --

  @Test
  public void theDescendantsOfADroppedSpanAreDroppedWithIt() {
    InMemorySpanExporter exported = InMemorySpanExporter.create();
    CustomSampler sampler = new CustomSampler();
    sampler.setConfig(new SamplingConfig(Arrays.asList(
        new SamplingConfig.SpanSamplingConfig(SamplingConfig.MatchConfig.ofValue("root"), null, null, 0),
        new SamplingConfig.SpanSamplingConfig(SamplingConfig.MatchConfig.ofValue("kept"), null, null, 1)), null));
    SamplingTraceExporter exporter = new SamplingTraceExporter(exported, sampler, logger);
    exporter.export(Arrays.asList(span("root", 1, 0), span("child", 2, 1), span("grandchild", 3, 2),
        span("kept", 4, 0), span("other", 5, 0)));

    List<String> names = new ArrayList<>();
    exported.getFinishedSpanItems().forEach(s -> names.add(s.getName()));
    assertThat(names, containsInAnyOrder("kept", "other"));
    SpanData kept = exported.getFinishedSpanItems().get(0);
    assertEquals(Long.valueOf(1), kept.getAttributes().get(AttributeKey.longKey("launchdarkly.sampling.ratio")));
    assertEquals("the span's own attributes stay", "b", kept.getAttributes().get(AttributeKey.stringKey("a")));
    assertEquals(2, kept.getTotalAttributeCount());
    assertNull("an unmatched span is unchanged",
        exported.getFinishedSpanItems().get(1).getAttributes().get(AttributeKey.longKey("launchdarkly.sampling.ratio")));
  }

  @Test
  public void aBatchWithNothingLeftIsNotSent() {
    InMemorySpanExporter exported = InMemorySpanExporter.create();
    CustomSampler sampler = new CustomSampler();
    sampler.setConfig(dropSpansNamed("drop"));
    SamplingTraceExporter exporter = new SamplingTraceExporter(exported, sampler, logger);
    assertTrue(exporter.export(Collections.singletonList(span("drop", 1, 0))).isSuccess());
    assertTrue(exported.getFinishedSpanItems().isEmpty());
    assertTrue(exporter.flush().isSuccess());
    assertTrue(exporter.shutdown().isSuccess());
  }

  @Test
  public void withoutRulesEverySpanIsExported() {
    InMemorySpanExporter exported = InMemorySpanExporter.create();
    new SamplingTraceExporter(exported, new CustomSampler(), logger).export(Collections.singletonList(span("s", 1, 0)));
    assertEquals(1, exported.getFinishedSpanItems().size());
  }

  @Test
  public void aSamplingFailureExportsTheBatchUnsampledAndIsLoggedOnce() {
    InMemorySpanExporter exported = InMemorySpanExporter.create();
    CustomSampler sampler = new CustomSampler(ratio -> {
      throw new IllegalStateException("sampler broke");
    });
    sampler.setConfig(dropSpansNamed("s"));
    SamplingTraceExporter exporter = new SamplingTraceExporter(exported, sampler, logger);
    exporter.export(Collections.singletonList(span("s", 1, 0)));
    exporter.export(Collections.singletonList(span("s", 2, 0)));
    assertEquals(2, exported.getFinishedSpanItems().size());
    assertEquals(1, logCapture.getMessages().size());
  }

  // -- the log processor --

  private static final class Recorder implements LogRecordProcessor {
    final List<ReadWriteLogRecord> records = new ArrayList<>();

    @Override
    public void onEmit(Context context, ReadWriteLogRecord logRecord) {
      records.add(logRecord);
    }
  }

  private static void emit(LogRecordProcessor processor, String body) {
    SdkLoggerProvider provider = SdkLoggerProvider.builder().addLogRecordProcessor(processor).build();
    provider.get("test").logRecordBuilder().setBody(body).setSeverity(Severity.INFO).setSeverityText("INFO").emit();
  }

  @Test
  public void logsAreSampledBeforeBeingPassedOn() {
    Recorder recorder = new Recorder();
    CustomSampler sampler = new CustomSampler(ratio -> ratio == 1);
    sampler.setConfig(new SamplingConfig(null, Arrays.asList(
        new SamplingConfig.LogSamplingConfig(SamplingConfig.MatchConfig.ofValue("drop"), null, null, 5),
        new SamplingConfig.LogSamplingConfig(SamplingConfig.MatchConfig.ofValue("keep"), null, null, 1))));
    SamplingLogProcessor processor = new SamplingLogProcessor(recorder, sampler, logger);
    emit(processor, "drop");
    emit(processor, "keep");
    emit(processor, "unmatched");
    assertEquals(2, recorder.records.size());
    assertEquals(Long.valueOf(1),
        recorder.records.get(0).toLogRecordData().getAttributes().get(AttributeKey.longKey("launchdarkly.sampling.ratio")));
    assertTrue(recorder.records.get(1).toLogRecordData().getAttributes().isEmpty());
    assertTrue(processor.forceFlush().isSuccess());
    assertTrue(processor.shutdown().isSuccess());
  }

  @Test
  public void aLogSamplingFailureKeepsTheRecord() {
    Recorder recorder = new Recorder();
    CustomSampler sampler = new CustomSampler(ratio -> {
      throw new IllegalStateException("sampler broke");
    });
    sampler.setConfig(new SamplingConfig(null, Collections.singletonList(
        new SamplingConfig.LogSamplingConfig(null, null, null, 2))));
    SamplingLogProcessor processor = new SamplingLogProcessor(recorder, sampler, logger);
    emit(processor, "a");
    emit(processor, "b");
    assertEquals(2, recorder.records.size());
    assertEquals(1, logCapture.getMessages().size());
  }

  // -- matching --

  @Test
  public void matchValuesFollowTheirTypes() {
    assertTrue(SamplingConfig.MatchConfig.ofValue("1").matches(1L));
    assertTrue(SamplingConfig.MatchConfig.ofValue(1.0).matches(1L));
    assertFalse(SamplingConfig.MatchConfig.ofValue(1.0).matches("1"));
    assertTrue(SamplingConfig.MatchConfig.ofValue(true).matches(true));
    assertFalse(SamplingConfig.MatchConfig.ofValue(true).matches("true"));
    assertFalse(SamplingConfig.MatchConfig.ofValue(LDValue.buildArray().build()).matches("x"));
    assertFalse(SamplingConfig.MatchConfig.ofValue("x").matches(null));
    assertTrue(SamplingConfig.MatchConfig.ofRegex("^ab").matches("abc"));
    assertFalse(SamplingConfig.MatchConfig.ofRegex("^ab").matches(1L));
    SamplingConfig.MatchConfig invalid = SamplingConfig.MatchConfig.ofRegex("(");
    assertFalse(invalid.isValid());
    assertTrue(invalid.isRegex());
    assertEquals("(", invalid.getRegexPattern());
    assertFalse(invalid.matches("("));
    assertTrue(SamplingConfig.MatchConfig.ofValue("x").isValid());
    assertEquals("x", SamplingConfig.MatchConfig.ofValue("x").getValue());
  }

  @Test
  public void theDefaultSamplerKeepsOneInRatio() {
    assertFalse(CustomSampler.defaultSampler(0));
    assertFalse(CustomSampler.defaultSampler(-1));
    assertTrue(CustomSampler.defaultSampler(1));
    int kept = 0;
    for (int i = 0; i < 10_000; i++) {
      if (CustomSampler.defaultSampler(10)) {
        kept++;
      }
    }
    assertTrue("kept " + kept, kept > 700 && kept < 1300);
    assertTrue(CustomSampler.matchesValue(null, null));
    assertFalse(CustomSampler.matchesValue(SamplingConfig.MatchConfig.ofValue("x"), null));
    assertTrue(new CustomSampler().sampleSpan(span("s", 1, 0)).isSampled());
  }

  @Test
  public void aConfigDescribesItsSize() {
    assertEquals("SamplingConfig{spans=0, logs=0}", new SamplingConfig(null, null).toString());
    SamplingConfig.SpanEventMatchConfig event = new SamplingConfig.SpanEventMatchConfig(null, null);
    assertTrue(event.getAttributes().isEmpty());
    SamplingConfig.LogSamplingConfig log = new SamplingConfig.LogSamplingConfig(null, null, null, 3);
    assertEquals(3, log.getSamplingRatio());
  }

  // -- parsing and fetching --

  @Test
  public void malformedFieldsCountAsAbsent() {
    SamplingConfig config = SamplingApiService.parseSamplingConfig(LDValue.parse("{"
        + "\"spans\":[{\"name\":{\"regexValue\":\"\"},\"attributes\":\"nope\",\"events\":[1,{\"name\":null}],"
        + "\"samplingRatio\":\"x\"},"
        + "{\"name\":{\"regexValue\":\"(\"}}, 5],"
        + "\"logs\":{\"not\":\"an array\"}}"), logger);
    assertEquals(2, config.getSpans().size());
    SamplingConfig.SpanSamplingConfig first = config.getSpans().get(0);
    assertNull("an empty regex is no rule", first.getName());
    assertEquals(1, first.getSamplingRatio());
    assertEquals(1, first.getEvents().size());
    assertNull(first.getEvents().get(0).getName());
    assertFalse(config.getSpans().get(1).getName().isValid());
    assertThat(logCapture.getMessageStrings().toString(), containsString("does not compile"));
    assertTrue(config.getLogs().isEmpty());
  }

  @Test
  public void matchValuesKeepTheirJsonType() {
    SamplingConfig config = SamplingApiService.parseSamplingConfig(LDValue.parse("{\"logs\":["
        + "{\"message\":{\"matchValue\":true}},{\"message\":{\"matchValue\":2}},"
        + "{\"message\":{\"matchValue\":\"s\",\"regexValue\":\"ignored\"}},{\"message\":{\"matchValue\":[1]}},"
        + "{\"message\":{}}]}"), logger);
    assertEquals(Boolean.TRUE, config.getLogs().get(0).getMessage().getValue());
    assertEquals(2.0, config.getLogs().get(1).getMessage().getValue());
    assertEquals("s", config.getLogs().get(2).getMessage().getValue());
    assertFalse(config.getLogs().get(2).getMessage().isRegex());
    assertTrue(config.getLogs().get(3).getMessage().getValue() instanceof LDValue);
    assertNull(config.getLogs().get(4).getMessage());
  }

  private GraphQLClient client() {
    return new GraphQLClient(server.url("/").toString(), Collections.singletonMap("X-Test", "yes"), logger);
  }

  @Test
  public void theConfigIsFetchedWithAGzippedQuery() throws Exception {
    server.enqueue(new MockResponse().setBody("{\"data\":{\"sampling\":{\"spans\":[{\"samplingRatio\":7}],"
        + "\"logs\":[]}}}"));
    GraphQLClient client = client();
    SamplingConfig config = new SamplingApiService(client, logger).getSamplingConfig("mob-key");
    client.close();
    assertEquals(7, config.getSpans().get(0).getSamplingRatio());

    RecordedRequest request = server.takeRequest(5, TimeUnit.SECONDS);
    assertEquals("POST", request.getMethod());
    assertEquals("gzip", request.getHeader("Content-Encoding"));
    assertEquals("yes", request.getHeader("X-Test"));
    assertThat(request.getHeader("Content-Type"), containsString("application/json"));
    LDValue body = LDValue.parse(gunzip(request.getBody().readByteArray()));
    assertEquals(SamplingApiService.QUERY, body.get("query").stringValue());
    assertEquals("mob-key", body.get("variables").get("organization_verbose_id").stringValue());
  }

  @Test
  public void aFailedRequestGivesNoConfig() {
    server.enqueue(new MockResponse().setResponseCode(500));
    server.enqueue(new MockResponse().setBody("[1]"));
    server.enqueue(new MockResponse().setBody("{\"errors\":[{\"message\":\"bad\"}],\"data\":null}"));
    server.enqueue(new MockResponse().setBody("{\"data\":{\"sampling\":null}}"));
    server.enqueue(new MockResponse().setBody("not json"));
    SamplingApiService service = new SamplingApiService(client(), logger);
    for (int i = 0; i < 5; i++) {
      assertNull(service.getSamplingConfig("k"));
    }
    String logged = logCapture.getMessageStrings().toString();
    assertThat(logged, containsString("status 500"));
    assertThat(logged, containsString("not a JSON object"));
    assertThat(logged, containsString("bad"));
    assertThat(logged, containsString("Could not fetch"));
  }

  @Test
  public void anUnreachableBackendGivesNoConfig() throws Exception {
    String url = server.url("/").toString();
    server.shutdown();
    assertNull(new SamplingApiService(new GraphQLClient(url, Collections.<String, String>emptyMap(), logger), logger)
        .getSamplingConfig("k"));
  }

  @Test
  public void theResultCarriesItsAttributes() {
    CustomSampler.SamplingResult result = new CustomSampler.SamplingResult(false, Attributes.empty());
    assertFalse(result.isSampled());
    assertSame(Attributes.empty(), result.getAttributes());
    assertNotNull(CustomSampler.SamplingResult.SAMPLED);
  }

  static String gunzip(byte[] bytes) throws IOException {
    try (InputStream in = new GZIPInputStream(new java.io.ByteArrayInputStream(bytes))) {
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      byte[] buffer = new byte[4096];
      int n;
      while ((n = in.read(buffer)) > 0) {
        out.write(buffer, 0, n);
      }
      return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }
  }
}
