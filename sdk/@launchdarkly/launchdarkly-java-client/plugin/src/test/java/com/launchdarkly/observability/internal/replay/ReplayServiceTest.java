package com.launchdarkly.observability.internal.replay;

import com.launchdarkly.logging.LDLogger;
import com.launchdarkly.logging.LogCapture;
import com.launchdarkly.logging.Logs;
import com.launchdarkly.observability.ObservabilityOptions;
import com.launchdarkly.observability.PrivacyProfile;
import com.launchdarkly.observability.ReplayOptions;
import com.launchdarkly.observability.SessionReplayStartResult;
import com.launchdarkly.observability.internal.Click;
import com.launchdarkly.observability.internal.Version;
import com.launchdarkly.sdk.LDValue;

import io.opentelemetry.api.common.Attributes;

import org.junit.After;
import org.junit.Test;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.prefs.Preferences;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** The replay service: when it records, what it puts on the timeline, and how the backend's verdict steers it. */
public class ReplayServiceTest {
  /** A refusal store in memory. */
  static final class MemoryRefusals implements RefusalStore {
    String reason;
    int cleared;

    @Override
    public String load() {
      return reason;
    }

    @Override
    public void store(String reason) {
      this.reason = reason;
    }

    @Override
    public void clear() {
      reason = null;
      cleared++;
    }
  }

  private final ExporterTest.FakeApi api = new ExporterTest.FakeApi();
  private final MemoryRefusals refusals = new MemoryRefusals();
  private final LogCapture logs = Logs.capture();
  private final AtomicReference<PointerSink> sink = new AtomicReference<>();
  private final AtomicReference<String> session = new AtomicReference<>("s1");
  private final double[] random = {0.5};
  private final List<ReplayService> services = new ArrayList<>();
  /** The frame the source paints next, or null for no active window. */
  private final AtomicReference<BufferedImage> window = new AtomicReference<>();

  @After
  public void tearDown() {
    services.forEach(ReplayService::shutdown);
  }

  private ReplayService service(ReplayOptions options) {
    CaptureSource source = timeout -> {
      BufferedImage image = window.get();
      return image == null ? null : new CaptureSource.RawFrame(image, Collections.emptyList(), 1000, 100, 50, 1);
    };
    ObservabilityOptions observability = ObservabilityOptions.builder().serviceName("svc").serviceVersion("1.2")
        .build();
    Map<String, String> resource = new LinkedHashMap<>();
    resource.put("service.name", "svc");
    ReplayService service = new ReplayService("mob-key", observability, options, resource, null, session::get,
        LDLogger.withAdapter(logs, "test"), api, s -> {
          sink.set(s);
          return Collections.singletonList(source);
        }, refusals, () -> random[0], new InlineExecutor(), new InlineExecutor());
    services.add(service);
    return service;
  }

  private ReplayService service() {
    return service(ReplayOptions.builder().build());
  }

  private ReplayService recording() {
    ReplayService service = service();
    service.begin(true, false);
    api.calls.clear();
    return service;
  }

  /** The tags of the custom items queued. */
  private static List<String> tags(ReplayService service) {
    List<String> tags = new ArrayList<>();
    for (ReplayItem item : service.queue().peek(Long.MAX_VALUE, Integer.MAX_VALUE)) {
      if (item instanceof ReplayItem.Custom) {
        tags.add(((ReplayItem.Custom) item).tag);
      }
    }
    return tags;
  }

  private static ReplayItem.Custom last(ReplayService service) {
    List<ReplayItem> items = service.queue().peek(Long.MAX_VALUE, Integer.MAX_VALUE);
    return (ReplayItem.Custom) items.get(items.size() - 1);
  }

  /** The tags of the custom events pushed. */
  private List<String> pushedTags() {
    List<String> tags = new ArrayList<>();
    for (List<EventGenerator.Event> push : api.pushes) {
      for (EventGenerator.Event event : push) {
        LDValue json = LDValue.parse(EncodingTest.text(event));
        if (json.get("type").intValue() == RRWeb.CUSTOM) {
          tags.add(json.get("data").get("tag").stringValue());
        }
      }
    }
    return tags;
  }

  // -- starting and stopping --

  @Test
  public void beginningEnabledRecordsAndAsksTheBackendAtOnce() {
    ReplayService service = service();
    assertEquals("not begun", SessionReplayStartResult.UNAVAILABLE, service.start(false));
    service.begin(true, false);
    assertTrue(service.isEnabled());
    assertTrue(service.isRunning());
    assertFalse(service.loop().isPaused());
    assertEquals(Arrays.asList("init s1 mob-key svc 1.2 " + Version.VERSION, "identify s1 unknown"), api.calls);
    service.begin(true, false); // once only
    assertEquals(2, api.calls.size());
  }

  @Test
  public void beginningDisabledRecordsNothingUntilStarted() {
    ReplayService service = service(ReplayOptions.builder().enabled(false).build());
    service.begin(false, false);
    assertFalse(service.isEnabled());
    assertTrue(service.loop().isPaused());
    assertTrue(api.calls.isEmpty());
    assertEquals(SessionReplayStartResult.STARTED, service.start(false));
    assertEquals(SessionReplayStartResult.ALREADY_STARTED, service.start(false));
    assertFalse(service.loop().isPaused());
  }

  @Test
  public void stoppingPausesAndSetEnabledFollowsIt() {
    ReplayService service = recording();
    service.setEnabled(true); // already enabled
    assertTrue(api.calls.isEmpty());
    service.setEnabled(false);
    assertFalse(service.isEnabled());
    assertFalse(service.isRunning());
    assertTrue(service.loop().isPaused());
    service.setEnabled(true);
    assertTrue(service.isRunning());
  }

  @Test
  public void afterShutdownNothingStarts() {
    ReplayService service = recording();
    AtomicInteger stopped = new AtomicInteger();
    service.setOnStopped(stopped::incrementAndGet);
    service.stopped(); // the observability service stopped
    service.shutdown();
    assertEquals(1, stopped.get());
    assertFalse(service.isEnabled());
    assertEquals(SessionReplayStartResult.UNAVAILABLE, service.start(true));
  }

  // -- sampling --

  @Test
  public void samplingDecidesOncePerEnable() {
    ReplayService service = service(ReplayOptions.builder().sampleRate(0.5).build());
    random[0] = 0.6;
    service.begin(true, false);
    assertTrue("enabled, though sampled out", service.isEnabled());
    assertFalse(service.isRunning());
    random[0] = 0.1;
    assertEquals("already decided", SessionReplayStartResult.SAMPLED_OUT, service.start(false));
    assertEquals(SessionReplayStartResult.STARTED, service.start(true));
    service.stop();
    random[0] = 0.6;
    assertEquals(SessionReplayStartResult.SAMPLED_OUT, service.start(false));
    service.stop();
    random[0] = 0.1;
    assertEquals("a new enable decides again", SessionReplayStartResult.STARTED, service.start(false));
    assertThat(logs.getMessageStrings().toString(), org.hamcrest.Matchers.containsString("sampling left it out"));
  }

  @Test
  public void theSampleRateIsAShare() {
    ReplayService service = service();
    random[0] = 0.99;
    assertTrue(service.sample(1));
    assertFalse(service.sample(0));
    assertFalse(service.sample(-1));
    assertFalse(service.sample(0.5));
    random[0] = 0.2;
    assertTrue(service.sample(0.5));
  }

  // -- the backend's verdict --

  @Test
  public void aRefusalStopsReplayAndIsRememberedForTheNextLaunch() {
    ReplayService service = service();
    api.failNext("init", false);
    service.begin(true, false);
    assertFalse(service.isEnabled());
    assertFalse(service.isRunning());
    assertEquals("init failed", refusals.reason);
    assertEquals(0, service.queue().size());
    assertEquals(SessionReplayStartResult.UNRECOVERABLE_ERROR, service.start(true));
  }

  @Test
  public void afterARefusedLaunchScreenshotsWaitForTheBackend() {
    refusals.reason = "refused before";
    ReplayService service = service();
    api.failNext("init", true); // the first answer does not come
    service.begin(true, false);
    assertTrue(service.isRunning());
    assertTrue("no screenshots yet", service.loop().isPaused());
    assertThat(logs.getMessageStrings().toString(), org.hamcrest.Matchers.containsString("refused before"));

    service.foreground(); // coming back asks again
    assertFalse(service.loop().isPaused());
    assertNull(refusals.reason);
    assertEquals(1, refusals.cleared);
    service.foreground(); // nothing more to ask
    assertEquals(1, refusals.cleared);
  }

  @Test
  public void anAcceptedLaunchKeepsTakingScreenshots() {
    ReplayService service = recording();
    assertFalse(service.loop().isPaused());
    assertEquals("nothing to clear", 0, refusals.cleared);
  }

  // -- what goes on the timeline --

  @Test
  public void screensClicksAndTracksGoOnTheTimelineWhileRecording() {
    ReplayService service = recording();
    service.screenShown("Settings", "app.Settings", "app.Settings#1");
    service.click(new Click("JButton", "javax.swing.JButton", "save", "Save", "JFrame/JButton#save", 1, 2, 3, 4));
    String click = last(service).payloadJson;
    service.track("checkout", 9.5, Attributes.builder().put("plan", "pro").put("seats", 3L).build());
    String track = last(service).payloadJson;
    assertEquals(Arrays.asList(RRWeb.TAG_NAVIGATE, RRWeb.TAG_CLICK, RRWeb.TAG_TRACK), tags(service));
    LDValue clickJson = LDValue.parse(click);
    assertEquals("JButton", clickJson.get("clickTarget").stringValue());
    assertEquals("Save", clickJson.get("clickTextContent").stringValue());
    assertEquals("save", clickJson.get("clickSelector").stringValue());
    assertEquals("Settings", clickJson.get("screenName").stringValue());
    assertEquals("app.Settings#1", clickJson.get("screenId").stringValue());
    LDValue trackJson = LDValue.parse(LDValue.parse(track).stringValue());
    assertEquals("checkout", trackJson.get("event").stringValue());
    assertEquals(9.5, trackJson.get("value").doubleValue(), 0);
    assertEquals("pro", trackJson.get("data").get("plan").stringValue());
    assertEquals("3", trackJson.get("data").get("seats").stringValue());
  }

  @Test
  public void nothingGoesOnTheTimelineWhileStopped() {
    ReplayService service = service(ReplayOptions.builder().enabled(false).build());
    service.begin(false, false);
    service.screenShown("Settings", null, null);
    service.click(new Click("JButton", "javax.swing.JButton", null, null, "JButton", 1, 2, 3, 4));
    service.track("checkout", null, Attributes.empty());
    service.foreground();
    service.background();
    assertEquals(0, service.queue().size());
    assertTrue(api.calls.isEmpty());
  }

  @Test
  public void startingShowsTheScreenAlreadyOpen() {
    ReplayService service = service(ReplayOptions.builder().enabled(false).build());
    service.begin(false, false);
    service.screenShown("Main", "app.Main", "app.Main");
    service.start(false);
    assertEquals(Collections.singletonList(RRWeb.TAG_NAVIGATE), tags(service));
  }

  @Test
  public void theBackgroundPausesCaptureAndSendsWhatIsQueued() {
    ReplayService service = recording();
    service.background();
    assertTrue(service.loop().isPaused());
    assertEquals(0, service.queue().size());
    assertThat(pushedTags(), hasItem(RRWeb.TAG_BACKGROUND));
    service.foreground();
    assertFalse(service.loop().isPaused());
    assertEquals(Collections.singletonList(RRWeb.TAG_FOREGROUND), tags(service));
    service.flush();
    assertThat(pushedTags(), hasItem(RRWeb.TAG_FOREGROUND));
  }

  // -- identify --

  @Test
  public void anIdentifyWhileRecordingIsSentAtOnce() {
    ReplayService service = recording();
    Map<String, String> keys = new LinkedHashMap<>();
    keys.put("user", "u1");
    keys.put("org", "");
    service.identified(keys, "user:u1");
    assertThat(api.calls, contains("identify s1 user:u1"));
    ReplayItem.Identify identify = (ReplayItem.Identify) last(service);
    assertEquals("s1", identify.sessionId);
    assertEquals("svc", identify.attributes.get("service.name"));
    assertEquals("u1", identify.attributes.get("user"));
    assertFalse("an empty key is left out", identify.attributes.containsKey("org"));
    assertEquals("user:u1", identify.attributes.get("canonicalKey"));
    assertEquals("user:u1", identify.attributes.get("userIdentifier"));
  }

  @Test
  public void anIdentifyWhileStoppedWaitsForTheStart() {
    ReplayService service = service(ReplayOptions.builder().enabled(false).build());
    service.begin(false, false);
    service.identified(Collections.singletonMap("user", "u2"), "user:u2");
    assertTrue(api.calls.isEmpty());
    session.set("s2");
    service.start(false);
    ReplayItem.Identify identify = (ReplayItem.Identify) service.queue().peek(Long.MAX_VALUE, 10).get(0);
    assertEquals("re-stamped with the session it is sent in", "s2", identify.sessionId);
    // The session starts with who it belongs to now.
    assertEquals(Arrays.asList("init s2 mob-key svc 1.2 " + Version.VERSION, "identify s2 user:u2"), api.calls);
  }

  @Test
  public void aFriendlyNameIsTheKeyShown() {
    ReplayService service = new ReplayService("mob-key", ObservabilityOptions.builder().contextFriendlyName("Jane")
        .build(), ReplayOptions.builder().build(), Collections.emptyMap(), null, session::get, LDLogger.none(), api,
        s -> Collections.emptyList(), refusals, () -> 0, new InlineExecutor(), new InlineExecutor());
    services.add(service);
    ReplayItem.Identify identify = service.identify(Collections.singletonMap("user", "u1"), "user:u1", 5, "s1");
    assertEquals("Jane", identify.attributes.get("key"));
    assertEquals("user:u1", identify.attributes.get("canonicalKey"));
  }

  // -- frames and the mouse --

  @Test
  public void framesAndTheMouseAreRecordedOnTheFrame() {
    ReplayService service = recording();
    window.set(new BufferedImage(64, 64, BufferedImage.TYPE_INT_RGB));
    service.loop().tick();
    sink.get().pointer(ReplayItem.Pointer.Action.DOWN, 110, 60, 5);
    List<ReplayItem> items = service.queue().peek(Long.MAX_VALUE, 10);
    assertThat(items.get(0), instanceOf(ReplayItem.Frame.class));
    ReplayItem.Pointer down = (ReplayItem.Pointer) items.get(1);
    assertEquals(10, down.positions.get(0).x);
    assertEquals(10, down.positions.get(0).y);

    service.background();
    sink.get().pointer(ReplayItem.Pointer.Action.DOWN, 110, 60, 6);
    assertEquals("nothing recorded in the background", 0, service.queue().size());
  }

  // -- the toolkits' sources --

  @Test
  public void theRuntimesToolkitsEachHaveASource() throws Exception {
    List<CaptureSource> sources = ReplayService.sources(1, MaskRules.defaults(), LDLogger.none(), (a, x, y, t) -> { });
    assertThat(sources.get(0), instanceOf(SwingCapture.class));
    // The production capture asks AWT for its active window.
    java.awt.EventQueue.invokeAndWait(() -> ((SwingCapture) sources.get(0)).paint());
    boolean fx = !System.getProperty("java.specification.version").startsWith("1.");
    assertEquals(fx ? 2 : 1, sources.size());
    sources.forEach(CaptureSource::stop);
  }

  @Test
  public void aMissingJavaFxIsNoSource() {
    LogCapture capture = Logs.capture();
    LDLogger logger = LDLogger.withAdapter(capture, "test");
    List<CaptureSource> sources = new ArrayList<>();
    ReplayService.addFxCapture(sources, "no.such.Probe", ReplayService.FX_CAPTURE_CLASS, 1, MaskRules.defaults(),
        logger, null);
    ReplayService.addFxCapture(sources, "java.lang.String", "no.such.Capture", 1, MaskRules.defaults(), logger,
        null);
    assertTrue(sources.isEmpty());
    assertThat(capture.getMessageStrings().toString(), org.hamcrest.Matchers.containsString("not capturing JavaFX"));
  }

  @Test
  public void aPrivacyProfileSetsTheRules() {
    MaskRules rules = ReplayService.rules(PrivacyProfile.builder().maskTextInputs(false).maskText(true)
        .maskImages(true).addMaskName("secret").addUnmaskName("public").addMaskClass("app.Card").build());
    assertFalse(rules.maskTextInputs);
    assertTrue(rules.maskText);
    assertTrue(rules.maskImages);
    assertEquals(Collections.singleton("secret"), rules.maskNames);
    assertEquals(Collections.singleton("public"), rules.unmaskNames);
    assertEquals(Collections.singleton("app.Card"), rules.maskClasses);
    assertTrue(rules.unmaskClasses.isEmpty());
    assertFalse(rules.maskWebViews);
    assertFalse(rules.maskSensitive);
    assertEquals(0.02, rules.minimumAlpha, 0);

    MaskRules more = ReplayService.rules(PrivacyProfile.builder().maskWebViews(true).maskBySemanticsKeywords(true)
        .minimumAlpha(0.5).addUnmaskClass("app.Help").build());
    assertTrue(more.maskWebViews);
    assertTrue(more.maskSensitive);
    assertEquals(0.5, more.minimumAlpha, 0);
    assertEquals(Collections.singleton("app.Help"), more.unmaskClasses);
  }

  @Test
  public void debugLogsReplayInDetailWithThePluginsAdapter() {
    LogCapture capture = Logs.capture();
    ObservabilityOptions options = ObservabilityOptions.builder().logAdapter(capture).loggerName("ld").build();
    LDLogger plugin = LDLogger.none();
    assertTrue("without debug, the plugin's logger", ReplayService.logger(options, plugin, false) == plugin);
    ReplayService.logger(options, plugin, true).debug("in detail");
    assertThat(capture.getMessageStrings().toString(), org.hamcrest.Matchers.containsString("in detail"));
  }

  @Test
  public void aFailingTickHookSkipsOnlyItself() {
    ReplayService service = recording();
    service.loop().onTick = () -> {
      throw new IllegalStateException("boom");
    };
    window.set(new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB));
    service.loop().tick();
    assertThat(service.queue().peek(Long.MAX_VALUE, 10).get(0), instanceOf(ReplayItem.Frame.class));
  }

  @Test
  public void aWorkerTaskRunsOnItsThreadAndFailsQuietly() {
    InlineExecutor executor = new InlineExecutor();
    BatchWorker worker = new BatchWorker(new ReplayQueue(), items -> { }, LDLogger.none(), executor,
        System::currentTimeMillis, new java.util.Random());
    AtomicInteger ran = new AtomicInteger();
    worker.run(ran::incrementAndGet);
    worker.run(() -> {
      throw new IllegalStateException("boom");
    });
    worker.stop(10);
    worker.run(ran::incrementAndGet); // stopped: dropped
    assertEquals(1, ran.get());
  }

  // -- the refusal store --

  @Test
  public void refusalsAreKeptPerCredentialWithoutTheCredential() throws Exception {
    Preferences node = Preferences.userRoot().node("/com/launchdarkly/observability/replay-test-" + System.nanoTime());
    try {
      RefusalStore.InPreferences store = new RefusalStore.InPreferences(node, "mob-secret");
      RefusalStore other = new RefusalStore.InPreferences(node, "mob-other");
      assertNull(store.load());
      store.store("EU");
      assertEquals("EU", store.load());
      assertNull(other.load());
      for (String key : node.keys()) {
        assertFalse(key.contains("mob-secret"));
        assertThat(key, startsWith("refused."));
      }
      StringBuilder longReason = new StringBuilder();
      for (int i = 0; i < 2000; i++) {
        longReason.append('x');
      }
      store.store(longReason.toString());
      assertEquals(1024, store.load().length());
      store.store(null);
      assertEquals("", store.load());
      store.clear();
      assertNull(store.load());
    } finally {
      node.removeNode();
    }
  }

  @Test
  public void aStoreThatFailsRemembersNothing() throws Exception {
    Preferences node = Preferences.userRoot().node("/com/launchdarkly/observability/replay-test-" + System.nanoTime());
    RefusalStore.InPreferences store = new RefusalStore.InPreferences(node, "k");
    node.removeNode(); // every use now throws IllegalStateException
    assertNull(store.load());
    store.store("x");
    store.clear();
    assertNull(RefusalStore.NONE.load());
    RefusalStore.NONE.store("x");
    RefusalStore.NONE.clear();
    assertNull(RefusalStore.NONE.load());
  }
}
