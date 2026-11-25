package com.launchdarkly.observability.internal;

import com.launchdarkly.observability.Metric;
import com.launchdarkly.observability.ObservabilityOptions;
import com.launchdarkly.observability.internal.sampling.SamplingConfig;
import com.launchdarkly.sdk.client.integrations.EnvironmentMetadata;
import com.launchdarkly.sdk.client.integrations.SdkMetadata;
import com.launchdarkly.sdk.client.subsystems.ApplicationInfo;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.logs.Severity;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Scope;
import io.opentelemetry.sdk.logs.data.LogRecordData;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.metrics.data.MetricDataType;
import io.opentelemetry.sdk.trace.data.EventData;
import io.opentelemetry.sdk.trace.data.SpanData;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class ObservabilityServiceTest {
  private static final AttributeKey<String> SESSION_ID = AttributeKey.stringKey("session.id");

  private final TestTelemetry telemetry = new TestTelemetry();
  private final List<ObservabilityService> started = new ArrayList<>();
  private Thread.UncaughtExceptionHandler originalHandler;

  @Before
  public void setUp() {
    originalHandler = Thread.getDefaultUncaughtExceptionHandler();
    GlobalAttributes.clearForTest();
  }

  @After
  public void tearDown() {
    started.forEach(ObservabilityService::shutdown);
    Thread.setDefaultUncaughtExceptionHandler(originalHandler);
    GlobalAttributes.clearForTest();
  }

  private ObservabilityService service(ObservabilityOptions options) {
    return service(options, null);
  }

  private ObservabilityService service(ObservabilityOptions options, EnvironmentMetadata metadata) {
    ObservabilityService service = telemetry.service(options, metadata);
    started.add(service);
    return service;
  }

  private ObservabilityService quiet() {
    return service(TestTelemetry.quietOptions().build());
  }

  // -- resource and session --

  @Test
  public void everySignalCarriesTheResourceAndTheSession() {
    ObservabilityService service = quiet();
    service.startSpan("s", Attributes.empty()).end();
    service.recordLog("hello", Severity.INFO, Attributes.empty(), null);
    service.recordIncr(new Metric("m", 1));

    SpanData span = telemetry.finishedSpans(service).get(0);
    assertEquals(TestTelemetry.CREDENTIAL,
        span.getResource().getAttribute(AttributeKey.stringKey("highlight.project_id")));
    assertEquals(service.getSessionId(), span.getAttributes().get(SESSION_ID));
    assertEquals(service.getSessionId(), telemetry.finishedLogs(service).get(0).getAttributes().get(SESSION_ID));
    MetricData metric = telemetry.finishedMetrics(service).get(0);
    assertEquals(service.getSessionId(), metric.getData().getPoints().iterator().next().getAttributes().get(SESSION_ID));
    assertEquals("com.launchdarkly.observability", span.getInstrumentationScopeInfo().getName());
  }

  @Test
  public void aSessionIsReplacedAfterFourHours() {
    ObservabilityService service = quiet();
    String first = service.getSessionId();
    telemetry.clock.addAndGet(Constants.SESSION_MAX_LIFETIME_MILLIS);
    service.startSpan("s", Attributes.empty()).end();
    String second = telemetry.finishedSpans(service).get(0).getAttributes().get(SESSION_ID);
    assertFalse(first.equals(second));
    assertEquals(second, service.getSessionId());
  }

  @Test
  public void globalAttributesFillOnlyMissingKeysAndNeverTheSession() {
    GlobalAttributes.set(Attributes.builder().put("tier", "gold").put("region", "eu").build());
    ObservabilityService service = quiet();
    service.startSpan("s", Attributes.builder().put("tier", "silver").build()).end();
    service.recordLog("l", Severity.INFO, Attributes.builder().put("region", "us").build(), null);
    service.recordMetric(new Metric("g", 2, Attributes.builder().put("tier", "bronze").build()));

    Attributes spanAttributes = telemetry.finishedSpans(service).get(0).getAttributes();
    assertEquals("silver", spanAttributes.get(AttributeKey.stringKey("tier")));
    assertEquals("eu", spanAttributes.get(AttributeKey.stringKey("region")));
    Attributes logAttributes = telemetry.finishedLogs(service).get(0).getAttributes();
    assertEquals("gold", logAttributes.get(AttributeKey.stringKey("tier")));
    assertEquals("us", logAttributes.get(AttributeKey.stringKey("region")));
    Attributes pointAttributes = telemetry.finishedMetrics(service).get(0).getData().getPoints().iterator().next()
        .getAttributes();
    assertEquals("bronze", pointAttributes.get(AttributeKey.stringKey("tier")));
    assertEquals("eu", pointAttributes.get(AttributeKey.stringKey("region")));
  }

  @Test
  public void theResourceDescribesTheApplicationAndTheSdk() {
    EnvironmentMetadata metadata = new EnvironmentMetadata(new ApplicationInfo("app-id", "app-version"),
        new SdkMetadata("JavaClient", "0.1.0", null, null), TestTelemetry.CREDENTIAL);
    ObservabilityService service = service(TestTelemetry.quietOptions().serviceName("svc").serviceVersion("1.2").build(),
        metadata);
    service.startSpan("s", Attributes.empty()).end();
    Attributes resource = telemetry.finishedSpans(service).get(0).getResource().getAttributes();
    assertEquals("svc", resource.get(AttributeKey.stringKey("service.name")));
    assertEquals("1.2", resource.get(AttributeKey.stringKey("service.version")));
    assertEquals("app-id", resource.get(AttributeKey.stringKey("launchdarkly.application.id")));
    assertEquals("app-version", resource.get(AttributeKey.stringKey("launchdarkly.application.version")));
    assertEquals("JavaClient/0.1.0", resource.get(AttributeKey.stringKey("launchdarkly.sdk.version")));
    assertEquals(Constants.DISTRO_NAME, resource.get(AttributeKey.stringKey("telemetry.distro.name")));
  }

  // -- metrics --

  @Test
  public void eachMetricMethodRecordsItsInstrument() {
    ObservabilityService service = quiet();
    service.recordMetric(new Metric("gauge", 1.5));
    service.recordCount(new Metric("count", 3.9));
    service.recordIncr(new Metric("incr", 100));
    service.recordIncr(new Metric("incr", 100));
    service.recordHistogram(new Metric("histogram", 2.5));
    service.recordUpDownCounter(new Metric("updown", -2.2));

    List<MetricData> metrics = telemetry.finishedMetrics(service);
    assertEquals(MetricDataType.DOUBLE_GAUGE, named(metrics, "gauge").getType());
    assertEquals(1.5, named(metrics, "gauge").getDoubleGaugeData().getPoints().iterator().next().getValue(), 0);
    assertEquals(3, named(metrics, "count").getLongSumData().getPoints().iterator().next().getValue());
    assertEquals(2, named(metrics, "incr").getLongSumData().getPoints().iterator().next().getValue());
    assertEquals(1, named(metrics, "histogram").getHistogramData().getPoints().iterator().next().getCount());
    assertEquals(-2, named(metrics, "updown").getLongSumData().getPoints().iterator().next().getValue());
  }

  @Test
  public void metricsCanBeTurnedOff() {
    ObservabilityService service = service(TestTelemetry.quietOptions()
        .metricsApi(ObservabilityOptions.MetricsApi.disabled()).build());
    service.recordMetric(new Metric("gauge", 1));
    service.recordCount(new Metric("count", 1));
    service.recordIncr(new Metric("incr", 1));
    service.recordHistogram(new Metric("histogram", 1));
    service.recordUpDownCounter(new Metric("updown", 1));
    assertThat(telemetry.finishedMetrics(service), empty());
  }

  private static MetricData named(List<MetricData> metrics, String name) {
    for (MetricData metric : metrics) {
      if (metric.getName().equals(name)) {
        return metric;
      }
    }
    throw new AssertionError("no metric " + name);
  }

  // -- logs --

  @Test
  public void aLogCarriesItsSeverityAsNumberAndText() {
    ObservabilityService service = quiet();
    service.recordLog("disk low", Severity.WARN, Attributes.builder().put("free", 3L).build(), null);
    LogRecordData log = telemetry.finishedLogs(service).get(0);
    assertEquals("disk low", log.getBodyValue().asString());
    assertEquals(Severity.WARN, log.getSeverity());
    assertEquals("WARN", log.getSeverityText());
    assertEquals(Long.valueOf(3), log.getAttributes().get(AttributeKey.longKey("free")));
    assertTrue(log.getTimestampEpochNanos() > 0);
  }

  @Test
  public void logsBelowTheLevelAreDropped() {
    ObservabilityService service = service(TestTelemetry.quietOptions()
        .logsApiLevel(ObservabilityOptions.LogLevel.WARN).build());
    service.recordLog("info", Severity.INFO, Attributes.empty(), null);
    service.recordLog("warn", Severity.WARN, Attributes.empty(), null);
    service.getOpenTelemetry().getLogsBridge().get("other").logRecordBuilder().setBody("unset").emit();
    List<LogRecordData> logs = telemetry.finishedLogs(service);
    assertEquals(1, logs.size());
    assertEquals("warn", logs.get(0).getBodyValue().asString());
  }

  @Test
  public void aRecordWithoutSeverityIsExportedAtTraceLevel() {
    ObservabilityService service = service(TestTelemetry.quietOptions()
        .logsApiLevel(ObservabilityOptions.LogLevel.TRACE).build());
    service.getOpenTelemetry().getLogsBridge().get("other").logRecordBuilder().setBody("unset").emit();
    assertEquals(1, telemetry.finishedLogs(service).size());
  }

  @Test
  public void aLogIsCorrelatedWithTheGivenSpanOrTheCurrentOne() {
    ObservabilityService service = quiet();
    Span given = service.startSpan("given", Attributes.empty());
    Span current = service.startSpan("current", Attributes.empty());
    try (Scope ignored = current.makeCurrent()) {
      service.recordLog("to given", Severity.INFO, Attributes.empty(), given.getSpanContext());
      service.recordLog("to current", Severity.INFO, Attributes.empty(), null);
      service.recordLog("invalid context", Severity.INFO, Attributes.empty(), Span.getInvalid().getSpanContext());
    }
    given.end();
    current.end();
    List<LogRecordData> logs = telemetry.finishedLogs(service);
    assertEquals(given.getSpanContext(), logs.get(0).getSpanContext());
    assertEquals(current.getSpanContext(), logs.get(1).getSpanContext());
    assertEquals(current.getSpanContext(), logs.get(2).getSpanContext());
  }

  // -- errors and spans --

  @Test
  public void anErrorIsASpanCarryingTheException() {
    ObservabilityService service = quiet();
    Span parent = service.startSpan("parent", Attributes.empty());
    try (Scope ignored = parent.makeCurrent()) {
      service.recordError(new IllegalStateException("broken"), Attributes.builder().put("screen", "home").build());
    }
    parent.end();
    SpanData error = telemetry.spansNamed(service, "highlight.error").get(0);
    assertEquals(parent.getSpanContext().getSpanId(), error.getParentSpanId());
    assertEquals(StatusCode.ERROR, error.getStatus().getStatusCode());
    assertEquals("broken", error.getStatus().getDescription());
    assertEquals("home", error.getAttributes().get(AttributeKey.stringKey("screen")));
    EventData exception = error.getEvents().get(0);
    assertEquals("exception", exception.getName());
    assertEquals("java.lang.IllegalStateException", exception.getAttributes().get(AttributeKey.stringKey("exception.type")));
    assertEquals("home", exception.getAttributes().get(AttributeKey.stringKey("screen")));
  }

  @Test
  public void errorsAndSpansCanBeTurnedOff() {
    ObservabilityService service = service(TestTelemetry.quietOptions()
        .tracesApi(ObservabilityOptions.TracesApi.disabled()).build());
    service.recordError(new RuntimeException(), Attributes.empty());
    Span span = service.startSpan("s", Attributes.empty());
    assertFalse(span.getSpanContext().isValid());
    service.track("t", null, Attributes.empty(), null);
    assertThat(telemetry.finishedSpans(service), empty());
  }

  @Test
  public void spansAndErrorsAreSeparateOptions() {
    ObservabilityService service = service(TestTelemetry.quietOptions()
        .tracesApi(ObservabilityOptions.TracesApi.builder().includeSpans(false).build()).build());
    service.recordError(new RuntimeException("kept"), Attributes.empty());
    service.startSpan("dropped", Attributes.empty()).end();
    assertThat(names(telemetry.finishedSpans(service)), contains("highlight.error"));
  }

  // -- track --

  @Test
  public void aTrackSpanKeepsTheEventIdentityOverItsData() {
    ObservabilityService service = quiet();
    service.track("checkout", 9.5, Attributes.builder().put("key", "spoofed").put("cart", 3L).put("user", "data").build(),
        Attributes.builder().put("user", "u-1").build());
    Attributes attributes = telemetry.spansNamed(service, "track").get(0).getAttributes();
    assertEquals("checkout", attributes.get(AttributeKey.stringKey("key")));
    assertEquals(Double.valueOf(9.5), attributes.get(AttributeKey.doubleKey("value")));
    assertEquals(Long.valueOf(3), attributes.get(AttributeKey.longKey("cart")));
    assertEquals("context keys come after the data", "u-1", attributes.get(AttributeKey.stringKey("user")));
  }

  @Test
  public void aTrackWithoutContextKeysUsesTheLastIdentify() {
    ObservabilityService service = quiet();
    service.track("before", null, Attributes.empty(), null);
    service.afterIdentify(Attributes.builder().put("user", "u-2").build(), "u-2");
    service.track("after", null, Attributes.empty(), null);
    List<SpanData> tracks = telemetry.spansNamed(service, "track");
    assertNull(tracks.get(0).getAttributes().get(AttributeKey.stringKey("user")));
    assertNull(tracks.get(0).getAttributes().get(AttributeKey.doubleKey("value")));
    assertEquals("u-2", tracks.get(1).getAttributes().get(AttributeKey.stringKey("user")));
  }

  @Test
  public void trackSpansCanBeTurnedOff() {
    ObservabilityService service = service(TestTelemetry.quietOptions()
        .analytics(ObservabilityOptions.Analytics.builder().trackEvents(false).appLaunch(false).build()).build());
    service.track("t", null, Attributes.empty(), null);
    assertThat(telemetry.finishedSpans(service), empty());
  }

  // -- evaluations and identify --

  @Test
  public void anEvaluationIsASpanWithAFeatureFlagEvent() {
    ObservabilityService service = quiet();
    Span span = service.beforeEvaluation("flag", "user:u");
    service.afterEvaluation(span, "flag", "user:u", "true", 1, true);
    SpanData evaluation = telemetry.spansNamed(service, "evaluation").get(0);
    assertEquals("flag", evaluation.getAttributes().get(ObservabilityService.FEATURE_FLAG_KEY));
    assertEquals("LaunchDarkly", evaluation.getAttributes().get(ObservabilityService.FEATURE_FLAG_PROVIDER_NAME));
    assertEquals("user:u", evaluation.getAttributes().get(ObservabilityService.FEATURE_FLAG_CONTEXT_ID));
    EventData event = evaluation.getEvents().get(0);
    assertEquals("feature_flag", event.getName());
    assertEquals("true", event.getAttributes().get(ObservabilityService.FEATURE_FLAG_RESULT_VALUE));
    assertEquals(Long.valueOf(1), event.getAttributes().get(ObservabilityService.FEATURE_FLAG_VARIATION_INDEX));
    assertEquals(Boolean.TRUE, event.getAttributes().get(ObservabilityService.FEATURE_FLAG_IN_EXPERIMENT));
  }

  @Test
  public void anEvaluationWithoutAVariationOrReasonOmitsThem() {
    ObservabilityService service = quiet();
    service.afterEvaluation(service.beforeEvaluation("flag", "user:u"), "flag", "user:u", "null", null, null);
    Attributes event = telemetry.spansNamed(service, "evaluation").get(0).getEvents().get(0).getAttributes();
    assertNull(event.get(ObservabilityService.FEATURE_FLAG_VARIATION_INDEX));
    assertNull(event.get(ObservabilityService.FEATURE_FLAG_IN_EXPERIMENT));
  }

  @Test
  public void anIdentifyIsALogWithTheContextKeys() {
    ObservabilityService service = quiet();
    service.afterIdentify(Attributes.builder().put("user", "u").put("org", "o").build(), "org:o:user:u");
    LogRecordData log = telemetry.finishedLogs(service).get(0);
    assertEquals("LD.identify", log.getBodyValue().asString());
    assertEquals(Severity.INFO, log.getSeverity());
    assertEquals("u", log.getAttributes().get(AttributeKey.stringKey("user")));
    assertEquals("o", log.getAttributes().get(AttributeKey.stringKey("org")));
    assertEquals("org:o:user:u", log.getAttributes().get(AttributeKey.stringKey("key")));
    assertEquals("org:o:user:u", log.getAttributes().get(AttributeKey.stringKey("canonicalKey")));
    assertEquals("completed", log.getAttributes().get(AttributeKey.stringKey("identify.result.status")));
  }

  @Test
  public void anIdentifyUsesTheFriendlyNameWhenSet() {
    ObservabilityService service = service(TestTelemetry.quietOptions().contextFriendlyName("Pat").build());
    service.afterIdentify(Attributes.builder().put("user", "u").build(), "u");
    assertEquals("Pat", telemetry.finishedLogs(service).get(0).getAttributes().get(AttributeKey.stringKey("key")));
  }

  // -- launch --

  private ObservabilityOptions.Builder launchOptions() {
    return TestTelemetry.quietOptions().analytics(ObservabilityOptions.Analytics.builder().build());
  }

  private static EnvironmentMetadata application(String version, String versionName) {
    return new EnvironmentMetadata(new ApplicationInfo("app", null, version, versionName), null, "key");
  }

  private SpanData launchSpan(ObservabilityService service) {
    List<SpanData> launches = telemetry.spansNamed(service, "app_launch");
    assertEquals(1, launches.size());
    return launches.get(0);
  }

  @Test
  public void theFirstLaunchIsAnInstallAndTheVersionIsRemembered() {
    ObservabilityService service = service(launchOptions().build(), application("42", "1.0"));
    AppLaunch appLaunch = service.getAppLaunch();
    assertEquals("install", appLaunch.getLaunchType());
    assertEquals("1.0", appLaunch.getVersion());
    assertEquals("42", appLaunch.getBuild());
    assertNull(appLaunch.getPreviousVersion());
    assertTrue(appLaunch.getTimestamp() > 0);
    SpanData launch = launchSpan(service);
    assertEquals("install", launch.getAttributes().get(AttributeKey.stringKey("event.launch_type")));
    assertEquals("1.0", launch.getAttributes().get(AttributeKey.stringKey("event.version")));
    assertEquals("42", launch.getAttributes().get(AttributeKey.stringKey("event.build")));
    assertEquals("1.0", telemetry.versions.values.get("version"));
    assertEquals("without launch time the span is a moment", launch.getStartEpochNanos(), launch.getEndEpochNanos());
    assertThat(launch.getEvents(), empty());
  }

  @Test
  public void aNewVersionIsAnUpdate() {
    telemetry.versions.values.put("version", "0.9");
    SpanData launch = launchSpan(service(launchOptions().build(), application("1.0", null)));
    assertEquals("update", launch.getAttributes().get(AttributeKey.stringKey("event.launch_type")));
    assertEquals("1.0", launch.getAttributes().get(AttributeKey.stringKey("event.version")));
    assertNull(launch.getAttributes().get(AttributeKey.stringKey("event.build")));
    assertEquals("0.9", launch.getAttributes().get(AttributeKey.stringKey("event.previous_version")));
  }

  @Test
  public void theSameVersionIsARelaunch() {
    telemetry.versions.values.put("version", "2.0");
    SpanData launch = launchSpan(service(launchOptions().serviceVersion("2.0").build()));
    assertEquals("the service version stands in for the application's", "relaunch",
        launch.getAttributes().get(AttributeKey.stringKey("event.launch_type")));
  }

  @Test
  public void anUnknownVersionIsAnUnknownLaunch() {
    SpanData launch = launchSpan(service(launchOptions().build(), new EnvironmentMetadata(null, null, "key")));
    assertEquals("unknown", launch.getAttributes().get(AttributeKey.stringKey("event.launch_type")));
    assertNull(launch.getAttributes().get(AttributeKey.stringKey("event.version")));
    assertThat(telemetry.versions.values.keySet(), empty());
  }

  @Test
  public void withLaunchTimeTheSpanStartsWithTheJvm() {
    SpanData launch = launchSpan(service(launchOptions().instrumentations(
        ObservabilityOptions.Instrumentations.builder().crashReporting(false).launchTime(true).build()).build()));
    long jvmStart = TimeUnit.MILLISECONDS.toNanos(AppLaunch.jvmStartMillis());
    assertEquals(jvmStart, launch.getStartEpochNanos());
    EventData start = launch.getEvents().get(0);
    assertEquals("app.start", start.getName());
    assertEquals("cold", start.getAttributes().get(AttributeKey.stringKey("start.type")));
    long duration = start.getAttributes().get(AttributeKey.longKey("start.duration_ms"));
    assertEquals(TimeUnit.NANOSECONDS.toMillis(launch.getEndEpochNanos() - launch.getStartEpochNanos()), duration);
    assertThat(duration, greaterThanOrEqualTo(0L));
  }

  // -- crashes --

  @Test
  public void crashReportingRecordsTheErrorAndPassesItOn() throws Exception {
    List<Throwable> passedOn = new ArrayList<>();
    Thread.setDefaultUncaughtExceptionHandler((t, e) -> passedOn.add(e));
    ObservabilityService service = service(TestTelemetry.quietOptions()
        .instrumentations(ObservabilityOptions.Instrumentations.builder().build()).build());
    RuntimeException crash = new RuntimeException("crash");
    Thread thread = new Thread(() -> {
      throw crash;
    }, "worker-7");
    thread.start();
    thread.join();

    SpanData error = telemetry.spansNamed(service, "highlight.error").get(0);
    assertEquals("worker-7", error.getAttributes().get(AttributeKey.stringKey("thread.name")));
    assertEquals(Long.valueOf(thread.getId()), error.getAttributes().get(AttributeKey.longKey("thread.id")));
    assertThat(passedOn, contains(crash));
  }

  @Test
  public void crashesAreReportedEvenWhenErrorsAreOff() {
    ObservabilityService service = service(TestTelemetry.quietOptions()
        .tracesApi(ObservabilityOptions.TracesApi.disabled()).build());
    service.recordCrash(Thread.currentThread(), new RuntimeException("crash"));
    assertThat(names(telemetry.finishedSpans(service)), contains("highlight.error"));
  }

  @Test
  public void aFailedStartStopsWhatItStartedAndInstallsNoCrashHandler() {
    Thread.UncaughtExceptionHandler before = Thread.getDefaultUncaughtExceptionHandler();
    TestTelemetry.MemoryVersionStore failing = new TestTelemetry.MemoryVersionStore() {
      @Override
      public String read() {
        throw new NoClassDefFoundError("java/util/prefs/Preferences");
      }
    };
    try {
      new ObservabilityService(TestTelemetry.CREDENTIAL, launchOptions().instrumentations(
          ObservabilityOptions.Instrumentations.enabled()).serviceVersion("1").build(), null, telemetry.logger,
          telemetry.exporters(), telemetry.sessions, telemetry.sampler, failing, java.time.Duration.ofHours(1));
      throw new AssertionError("expected the start to fail");
    } catch (NoClassDefFoundError expected) {
      assertSame(before, Thread.getDefaultUncaughtExceptionHandler());
    }
  }

  @Test
  public void shutdownRemovesTheCrashHandler() {
    Thread.UncaughtExceptionHandler before = (t, e) -> { };
    Thread.setDefaultUncaughtExceptionHandler(before);
    ObservabilityService service = service(TestTelemetry.quietOptions()
        .instrumentations(ObservabilityOptions.Instrumentations.enabled()).build());
    assertTrue(Thread.getDefaultUncaughtExceptionHandler() instanceof CrashReporter);
    service.shutdown();
    assertSame(before, Thread.getDefaultUncaughtExceptionHandler());
  }

  // -- sampling --

  @Test
  public void theSamplingRulesApplyAtExport() {
    ObservabilityService service = quiet();
    service.getSampler().setConfig(new SamplingConfig(Collections.singletonList(
        new SamplingConfig.SpanSamplingConfig(SamplingConfig.MatchConfig.ofValue("noisy"), null, null, 10)),
        Collections.singletonList(new SamplingConfig.LogSamplingConfig(
            SamplingConfig.MatchConfig.ofValue("chatty"), null, null, 10))));
    service.startSpan("noisy", Attributes.empty()).end();
    service.startSpan("kept", Attributes.empty()).end();
    service.recordLog("chatty", Severity.INFO, Attributes.empty(), null);
    service.recordLog("kept", Severity.INFO, Attributes.empty(), null);
    assertThat(names(telemetry.finishedSpans(service)), contains("kept"));
    assertEquals(1, telemetry.finishedLogs(service).size());
  }

  // -- lifecycle --

  @Test
  public void flushExportsWithoutWaiting() throws Exception {
    ObservabilityService service = quiet();
    service.startSpan("s", Attributes.empty()).end();
    service.flush();
    long deadline = System.currentTimeMillis() + 5000;
    while (telemetry.spans.getFinishedSpanItems().isEmpty() && System.currentTimeMillis() < deadline) {
      Thread.sleep(10);
    }
    assertThat(names(telemetry.spans.getFinishedSpanItems()), hasItem("s"));
  }

  @Test
  public void shutdownExportsWhatIsQueuedAndStops() {
    ObservabilityService service = quiet();
    service.startSpan("before", Attributes.empty()).end();
    service.shutdown();
    service.startSpan("after", Attributes.empty()).end();
    assertThat(names(telemetry.spans.getFinishedSpanItems()), containsInAnyOrder("before"));
  }

  @Test
  public void itExposesItsOpenTelemetry() {
    ObservabilityService service = quiet();
    service.getOpenTelemetry().getTracer("custom").spanBuilder("custom-span").startSpan().end();
    SpanData span = telemetry.finishedSpans(service).get(0);
    assertEquals("custom-span", span.getName());
    assertNotNull(span.getAttributes().get(SESSION_ID));
  }

  @Test
  public void reservedAttributeKeysAreReported() {
    ObservabilityService service = quiet();
    service.logReservedAttributes(Collections.singleton("session.id"));
    assertTrue(telemetry.logCapture.getMessageStrings().toString().contains("session.id"));
  }

  static List<String> names(List<SpanData> spans) {
    List<String> names = new ArrayList<>();
    spans.forEach(s -> names.add(s.getName()));
    return names;
  }
}
