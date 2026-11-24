package com.launchdarkly.observability.internal;

import com.launchdarkly.observability.LDObserve;
import com.launchdarkly.observability.Metric;
import com.launchdarkly.observability.Observability;
import com.launchdarkly.observability.ObservabilityOptions;
import com.launchdarkly.sdk.ContextKind;
import com.launchdarkly.sdk.LDContext;
import com.launchdarkly.sdk.LDValue;
import com.launchdarkly.sdk.client.Components;
import com.launchdarkly.sdk.client.LDClient;
import com.launchdarkly.sdk.client.LDConfig;
import com.launchdarkly.sdk.client.integrations.EnvironmentMetadata;
import com.launchdarkly.sdk.client.integrations.Hook;
import com.launchdarkly.sdk.client.integrations.TestData;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.logs.Severity;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.sdk.logs.data.LogRecordData;
import io.opentelemetry.sdk.trace.data.EventData;
import io.opentelemetry.sdk.trace.data.SpanData;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.launchdarkly.observability.internal.ObservabilityServiceTest.names;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/** The plugin through a real {@code LDClient}, and {@code LDObserve} in front of it. */
public class PluginTest {
  private static final LDContext USER = LDContext.create("user-1");

  private final TestTelemetry telemetry = new TestTelemetry();
  private final TestData data = TestData.dataSource();
  private final List<LDClient> clients = new ArrayList<>();

  @Before
  public void setUp() {
    ObservabilityRuntime.setFactoryForTest(telemetry.factory());
    GlobalAttributes.clearForTest();
  }

  @After
  public void tearDown() throws Exception {
    for (LDClient client : clients) {
      client.close();
    }
    ObservabilityRuntime.resetForTest();
    GlobalAttributes.clearForTest();
  }

  private LDClient client(String credential, Observability plugin) throws Exception {
    LDConfig config = new LDConfig.Builder(LDConfig.Builder.AutoEnvAttributes.Disabled)
        .mobileKey(credential)
        .dataSource(data)
        .events(Components.noEvents())
        .disablePersistence()
        .diagnosticOptOut(true)
        .plugins(Components.plugins().setPlugins(Collections.singletonList(plugin)))
        .build();
    LDClient client = LDClient.create(config, USER);
    clients.add(client);
    client.start(java.time.Duration.ofSeconds(5)).get(5, TimeUnit.SECONDS);
    return client;
  }

  private LDClient client() throws Exception {
    return client(TestTelemetry.CREDENTIAL, new Observability(TestTelemetry.quietOptions().build()));
  }

  private ObservabilityService service() {
    ObservabilityService service = ObservabilityRuntime.current();
    assertNotNull("observability is not running", service);
    return service;
  }

  @Test
  public void theMetadataNamesThePlugin() {
    Observability plugin = new Observability();
    assertEquals("@launchdarkly/observability-java-client", plugin.getMetadata().getName());
    assertEquals(Version.VERSION, plugin.getMetadata().getVersion());
  }

  @Test
  public void repeatedEvaluationsAreOneSpanUntilTheResultChanges() throws Exception {
    data.update(data.flag("flag").variationForUser("user-1", true));
    LDClient client = client();
    for (int i = 0; i < 3; i++) {
      assertTrue(client.boolVariation("flag", false));
    }
    data.update(data.flag("flag").variationForUser("user-1", false));
    assertFalse(client.boolVariation("flag", true));

    List<SpanData> evaluations = telemetry.spansNamed(service(), "evaluation");
    assertEquals(2, evaluations.size());
    EventData first = evaluations.get(0).getEvents().get(0);
    assertEquals("flag", first.getAttributes().get(ObservabilityService.FEATURE_FLAG_KEY));
    assertEquals("true", first.getAttributes().get(ObservabilityService.FEATURE_FLAG_RESULT_VALUE));
    assertEquals(Long.valueOf(0), first.getAttributes().get(ObservabilityService.FEATURE_FLAG_VARIATION_INDEX));
    assertEquals(Boolean.FALSE, first.getAttributes().get(ObservabilityService.FEATURE_FLAG_IN_EXPERIMENT));
    assertEquals("user-1", evaluations.get(0).getAttributes().get(ObservabilityService.FEATURE_FLAG_CONTEXT_ID));
    assertEquals("false", evaluations.get(1).getEvents().get(0).getAttributes()
        .get(ObservabilityService.FEATURE_FLAG_RESULT_VALUE));
  }

  @Test
  public void anUnknownFlagHasNoVariation() throws Exception {
    LDClient client = client();
    assertEquals("default", client.stringVariation("missing", "default"));
    Attributes event = telemetry.spansNamed(service(), "evaluation").get(0).getEvents().get(0).getAttributes();
    assertEquals("\"default\"", event.get(ObservabilityService.FEATURE_FLAG_RESULT_VALUE));
    assertNull(event.get(ObservabilityService.FEATURE_FLAG_VARIATION_INDEX));
  }

  @Test
  public void anIdentifyIsLoggedAndItsKeysGoOnLaterTrackSpans() throws Exception {
    LDClient client = client();
    LDContext multi = LDContext.createMulti(LDContext.create("user-2"),
        LDContext.create(ContextKind.of("org"), "org-1"));
    client.identify(multi).get(5, TimeUnit.SECONDS);
    LDObserve.track("manual", null, null);

    List<LogRecordData> identifies = new ArrayList<>();
    for (LogRecordData log : telemetry.finishedLogs(service())) {
      if (log.getBodyValue().asString().equals("LD.identify")) {
        identifies.add(log);
      }
    }
    assertFalse(identifies.isEmpty());
    Attributes last = identifies.get(identifies.size() - 1).getAttributes();
    assertEquals("user-2", last.get(AttributeKey.stringKey("user")));
    assertEquals("org-1", last.get(AttributeKey.stringKey("org")));
    assertEquals(multi.getFullyQualifiedKey(), last.get(AttributeKey.stringKey("canonicalKey")));
    Attributes track = telemetry.spansNamed(service(), "track").get(0).getAttributes();
    assertEquals("user-2", track.get(AttributeKey.stringKey("user")));
    assertEquals("org-1", track.get(AttributeKey.stringKey("org")));
  }

  @Test
  public void aTrackCallIsATrackSpanWithItsData() throws Exception {
    LDClient client = client();
    client.trackMetric("purchase", LDValue.buildObject().put("sku", "A-1").put("qty", 2).put("nested",
        LDValue.buildObject().put("x", 1).build()).put("ok", true).build(), 19.99);
    client.track("no-data");
    client.trackData("scalar-data", LDValue.of(3));

    List<SpanData> tracks = telemetry.spansNamed(service(), "track");
    assertEquals(3, tracks.size());
    Attributes purchase = tracks.get(0).getAttributes();
    assertEquals("purchase", purchase.get(AttributeKey.stringKey("key")));
    assertEquals(Double.valueOf(19.99), purchase.get(AttributeKey.doubleKey("value")));
    assertEquals("A-1", purchase.get(AttributeKey.stringKey("sku")));
    assertEquals(Double.valueOf(2), purchase.get(AttributeKey.doubleKey("qty")));
    assertEquals(Boolean.TRUE, purchase.get(AttributeKey.booleanKey("ok")));
    assertNull("an object member is skipped", purchase.get(AttributeKey.stringKey("nested")));
    assertEquals("user-1", purchase.get(AttributeKey.stringKey("user")));
    assertEquals("scalar-data", tracks.get(2).getAttributes().get(AttributeKey.stringKey("key")));
  }

  @Test
  public void aDisabledPluginAddsNoHookAndStartsNothing() throws Exception {
    Observability plugin = new Observability(ObservabilityOptions.builder().enabled(false).build());
    assertThat(plugin.getHooks(null), empty());
    LDClient client = client(TestTelemetry.CREDENTIAL, plugin);
    client.boolVariation("flag", false);
    assertNull(ObservabilityRuntime.current());
  }

  @Test
  public void aHookRecordsNothingUntilRegistered() {
    Observability plugin = new Observability(TestTelemetry.quietOptions().build());
    List<Hook> hooks = plugin.getHooks(new EnvironmentMetadata(null, null, TestTelemetry.CREDENTIAL));
    assertEquals(1, hooks.size());
    assertEquals("the hook is deduplicated", "com.launchdarkly.sdk.client.integrations.DedupingHook",
        hooks.get(0).getClass().getName());
  }

  @Test
  public void aSecondCredentialIsRefusedAndRecordsNothing() throws Exception {
    LDObserve.init(TestTelemetry.CREDENTIAL, TestTelemetry.quietOptions().build());
    ObservabilityService first = service();
    data.update(data.flag("flag").booleanFlag());
    LDClient other = client("mob-other", new Observability(TestTelemetry.quietOptions()
        .logAdapter(telemetry.logCapture).build()));
    other.boolVariation("flag", false);
    assertSame(first, ObservabilityRuntime.current());
    assertThat(telemetry.spansNamed(first, "evaluation"), empty());
    assertTrue(telemetry.logCapture.getMessageStrings().toString().contains("already running for another credential"));
  }

  @Test
  public void theHookFollowsARestart() throws Exception {
    LDClient client = client();
    LDObserve.shutdown();
    client.boolVariation("before-restart", false);
    LDObserve.init(TestTelemetry.CREDENTIAL, TestTelemetry.quietOptions().build());
    client.boolVariation("after-restart", false);
    List<SpanData> evaluations = telemetry.spansNamed(service(), "evaluation");
    assertEquals(1, evaluations.size());
    assertEquals("after-restart", evaluations.get(0).getAttributes().get(ObservabilityService.FEATURE_FLAG_KEY));
  }

  @Test
  public void onePluginSharedByTwoClientsRecordsOnlyTheFirstCredential() throws Exception {
    Observability shared = new Observability(TestTelemetry.quietOptions().logAdapter(telemetry.logCapture).build());
    LDClient first = client(TestTelemetry.CREDENTIAL, shared);
    first.close();
    clients.remove(first);
    LDClient second = client("mob-other", shared);
    second.boolVariation("other-flag", false);
    assertThat("the second credential's client records nothing", telemetry.spansNamed(service(), "evaluation"),
        empty());
  }

  @Test
  public void thePluginJoinsObservabilityStartedForTheSameCredential() throws Exception {
    LDObserve.init(TestTelemetry.CREDENTIAL, TestTelemetry.quietOptions().build());
    ObservabilityService first = service();
    client().boolVariation("flag", false);
    assertSame(first, ObservabilityRuntime.current());
    assertEquals(1, telemetry.spansNamed(first, "evaluation").size());
  }

  // -- LDObserve --

  @Test
  public void everyCallIsSafeBeforeObservabilityStarts() {
    assertNull(ObservabilityRuntime.current());
    LDObserve.recordMetric(new Metric("m", 1));
    LDObserve.recordCount(new Metric("m", 1));
    LDObserve.recordIncr(new Metric("m", 1));
    LDObserve.recordHistogram(new Metric("m", 1));
    LDObserve.recordUpDownCounter(new Metric("m", 1));
    LDObserve.recordLog("m", Severity.INFO, Attributes.empty());
    LDObserve.recordError(new RuntimeException(), Attributes.empty());
    LDObserve.track("t", null, null);
    LDObserve.flush();
    LDObserve.shutdown();
    assertFalse(LDObserve.startSpan("s", Attributes.empty()).getSpanContext().isValid());
    assertNull(LDObserve.getSessionId());
    assertNotNull(LDObserve.getOpenTelemetry().getTracer("t"));
    LDObserve.setAttributes(Attributes.builder().put("session.id", "spoof").build());
    assertTrue(GlobalAttributes.get().isEmpty());
  }

  @Test
  public void nullArgumentsRecordNothing() {
    LDObserve.init(TestTelemetry.CREDENTIAL, TestTelemetry.quietOptions().build());
    LDObserve.recordMetric(null);
    LDObserve.recordCount(null);
    LDObserve.recordIncr(null);
    LDObserve.recordHistogram(null);
    LDObserve.recordUpDownCounter(null);
    LDObserve.recordLog(null, Severity.INFO, Attributes.empty());
    LDObserve.recordError(null, Attributes.empty());
    LDObserve.track(null, null, null);
    assertFalse(LDObserve.startSpan(null, Attributes.empty()).getSpanContext().isValid());
    assertThat(telemetry.finishedSpans(service()), empty());
    assertThat(telemetry.finishedLogs(service()), empty());
    assertThat(telemetry.finishedMetrics(service()), empty());
  }

  @Test
  public void ldObserveRecordsThroughTheRunningService() {
    LDObserve.init(TestTelemetry.CREDENTIAL, TestTelemetry.quietOptions().build());
    LDObserve.recordMetric(new Metric("gauge", 1));
    LDObserve.recordCount(new Metric("count", 2));
    LDObserve.recordIncr(new Metric("incr", 0));
    LDObserve.recordHistogram(new Metric("histogram", 3));
    LDObserve.recordUpDownCounter(new Metric("updown", 4));
    LDObserve.recordLog("attributes", null, (Attributes) null);
    Map<String, Object> properties = new HashMap<>();
    properties.put("n", 1);
    LDObserve.recordLog("properties", Severity.ERROR, properties);
    LDObserve.recordError(new RuntimeException("e"), null);
    Span span = LDObserve.startSpan("map-span", properties);
    span.end();
    LDObserve.startSpan("null-attributes", (Attributes) null).end();
    LDObserve.track("event", properties, 1.0);
    LDObserve.setAttributes(properties);
    LDObserve.flush();

    ObservabilityService service = service();
    assertEquals(service.getSessionId(), LDObserve.getSessionId());
    assertSame(service.getOpenTelemetry(), LDObserve.getOpenTelemetry());
    assertThat(names(telemetry.finishedSpans(service)),
        contains("highlight.error", "map-span", "null-attributes", "track"));
    List<LogRecordData> logs = telemetry.finishedLogs(service);
    assertEquals(Severity.INFO, logs.get(0).getSeverity());
    assertEquals(Long.valueOf(1), logs.get(1).getAttributes().get(AttributeKey.longKey("n")));
    assertEquals(5, telemetry.finishedMetrics(service).size());
    assertEquals(Long.valueOf(1), GlobalAttributes.get().get(AttributeKey.longKey("n")));

    LDObserve.removeAttributes(Collections.singleton("n"));
    assertTrue(GlobalAttributes.get().isEmpty());
  }

  @Test
  public void reservedGlobalKeysAreReportedWhileRunning() {
    LDObserve.init(TestTelemetry.CREDENTIAL, TestTelemetry.quietOptions().build());
    LDObserve.setAttributes(Attributes.builder().put("session.id", "spoof").put("ok", "yes").build());
    assertNull(GlobalAttributes.get().get(AttributeKey.stringKey("session.id")));
    assertEquals("yes", GlobalAttributes.get().get(AttributeKey.stringKey("ok")));
    assertTrue(telemetry.logCapture.getMessageStrings().toString().contains("session.id"));
  }

  @Test
  public void initDoesNothingWhenDisabled() {
    LDObserve.init(TestTelemetry.CREDENTIAL, ObservabilityOptions.builder().enabled(false).build());
    assertNull(ObservabilityRuntime.current());
  }

  @Test
  public void shutdownStopsAndInitStartsAgain() {
    LDObserve.init(TestTelemetry.CREDENTIAL, TestTelemetry.quietOptions().build());
    ObservabilityService first = service();
    LDObserve.shutdown();
    assertNull(ObservabilityRuntime.current());
    LDObserve.init(TestTelemetry.CREDENTIAL, TestTelemetry.quietOptions().build());
    assertFalse(first == ObservabilityRuntime.current());
  }

  @Test
  public void aServiceThatCannotStartLeavesTheApplicationRunning() {
    ObservabilityRuntime.setFactoryForTest((credential, options, metadata, logger) -> {
      throw new NoSuchMethodError("old OpenTelemetry");
    });
    LDObserve.init(TestTelemetry.CREDENTIAL, ObservabilityOptions.builder().shutdownHook(false)
        .logAdapter(telemetry.logCapture).build());
    assertNull(ObservabilityRuntime.current());
    assertTrue(telemetry.logCapture.getMessageStrings().toString().contains("could not start"));
  }

  @Test
  public void theShutdownHookIsRegisteredOnce() {
    LDObserve.init(TestTelemetry.CREDENTIAL, TestTelemetry.quietOptions().shutdownHook(true).build());
    Thread hook = ObservabilityRuntime.shutdownHookForTest();
    assertNotNull(hook);
    LDObserve.shutdown();
    LDObserve.init(TestTelemetry.CREDENTIAL, TestTelemetry.quietOptions().shutdownHook(true).build());
    assertSame(hook, ObservabilityRuntime.shutdownHookForTest());
    // Running the hook stops observability, as on JVM exit.
    hook.run();
    assertNull(ObservabilityRuntime.current());
  }

  @Test
  public void aDefaultNamedApplicationIsKeyedByItsMainClass() {
    ObservabilityOptions defaults = ObservabilityOptions.builder().build();
    assertEquals("observability-java-client/com.example.Main",
        ObservabilityRuntime.applicationKey(defaults, null, "com.example.Main --flag"));
    assertEquals("observability-java-client", ObservabilityRuntime.applicationKey(defaults, null, null));
    assertEquals("observability-java-client", ObservabilityRuntime.applicationKey(defaults, null, "  "));
    assertEquals("svc", ObservabilityRuntime.applicationKey(ObservabilityOptions.builder().serviceName("svc").build(),
        null, "com.example.Main"));
  }

  @Test
  public void theApplicationKeyPrefersTheApplicationId() {
    ObservabilityOptions options = ObservabilityOptions.builder().serviceName("svc").build();
    assertEquals("svc", ObservabilityRuntime.applicationKey(options, null));
    assertEquals("svc", ObservabilityRuntime.applicationKey(options, new EnvironmentMetadata(null, null, "k")));
    assertEquals("app", ObservabilityRuntime.applicationKey(options, new EnvironmentMetadata(
        new com.launchdarkly.sdk.client.subsystems.ApplicationInfo("app", "1"), null, "k")));
  }
}
