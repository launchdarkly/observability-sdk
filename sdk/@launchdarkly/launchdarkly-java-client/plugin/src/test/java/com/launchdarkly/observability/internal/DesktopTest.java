package com.launchdarkly.observability.internal;

import com.launchdarkly.logging.LDLogger;
import com.launchdarkly.observability.LDObserve;
import com.launchdarkly.observability.ObservabilityOptions;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.sdk.trace.data.SpanData;

import org.junit.After;
import org.junit.Assume;
import org.junit.Test;

import java.awt.AWTEvent;
import java.awt.Button;
import java.awt.Component;
import java.awt.GraphicsEnvironment;
import java.awt.Label;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.AWTEventListener;
import java.awt.event.AWTEventListenerProxy;
import java.awt.event.MouseEvent;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JWindow;

import static com.launchdarkly.observability.internal.ObservabilityServiceTest.names;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** Windows, clicks and the foreground: the instrumentation, and the spans the service makes of them. */
public class DesktopTest {
  private final TestTelemetry telemetry = new TestTelemetry();
  private final List<Window> windows = new ArrayList<>();
  private final List<DesktopInstrumentation> instrumentations = new ArrayList<>();
  private ObservabilityService service;

  @After
  public void tearDown() {
    instrumentations.forEach(DesktopInstrumentation::stop);
    windows.forEach(Window::dispose);
    if (service != null) {
      service.shutdown();
    }
    ObservabilityRuntime.resetForTest();
  }

  private ObservabilityService service(ObservabilityOptions options) {
    service = telemetry.service(options);
    return service;
  }

  private ObservabilityService quiet() {
    return service(TestTelemetry.quietOptions().build());
  }

  private static void assumeDisplay() {
    Assume.assumeFalse("needs a display", GraphicsEnvironment.isHeadless());
  }

  private <T extends Window> T window(T window) {
    windows.add(window);
    return window;
  }

  // -- the session --

  @Test
  public void aSessionEndsAfterTheBackgroundTimeoutWithoutSignals() {
    AtomicLong clock = new AtomicLong(1000);
    SessionManager sessions = new SessionManager(1_000_000, 100, clock::get, new Random(3)); // DevSkim: ignore DS148264
    String first = sessions.getSessionId();
    clock.addAndGet(500);
    assertEquals("the foreground never times out", first, sessions.getSessionId());

    sessions.onBackgrounded();
    clock.addAndGet(99);
    assertEquals(first, sessions.getSessionId());
    clock.addAndGet(99);
    assertEquals("each signal keeps the session alive", first, sessions.getSessionId());
    clock.addAndGet(100);
    String second = sessions.getSessionId();
    assertNotEquals(first, second);

    clock.addAndGet(200);
    sessions.onForegrounded();
    String third = sessions.getSessionId();
    assertNotEquals("the first signal back still checks the timeout", second, third);
    clock.addAndGet(1000);
    assertEquals("then the foreground never times out", third, sessions.getSessionId());
    sessions.onForegrounded();
    assertEquals(third, sessions.getSessionId());
  }

  @Test
  public void aBackwardClockEndsABackgroundSession() {
    AtomicLong clock = new AtomicLong(1000);
    SessionManager sessions = new SessionManager(1_000_000, 100, clock::get, new Random(3)); // DevSkim: ignore DS148264
    String first = sessions.getSessionId();
    sessions.onBackgrounded();
    clock.set(990);
    assertNotEquals(first, sessions.getSessionId());
  }

  // -- the screen stack --

  @Test
  public void theScreenStackMatchesAndroid() {
    ScreenStack stack = new ScreenStack();
    assertNull(stack.currentScreenName());
    assertNull(stack.currentScreenId());
    ScreenStack.Change first = stack.record("Home", null);
    assertTrue(first.changed);
    assertNull(first.previous);
    assertFalse("the current screen again", stack.record("Home", null).changed);
    assertEquals("Home", stack.record("List", "list-1").previous);
    assertEquals("list-1", stack.currentScreenId());
    assertEquals("List", stack.record("Detail", "detail").previous);
    ScreenStack.Change back = stack.record("Home", null);
    assertEquals("back to Home reports where it came from", "Detail", back.previous);
    assertEquals("Home", stack.currentScreenName());
    assertTrue("List was popped off", stack.record("List", "list-1").changed);
    assertTrue("a second list with its own id", stack.record("List", "list-2").changed);
    stack.reset();
    assertNull(stack.record("Home", null).previous);
  }

  // -- the spans --

  @Test
  public void foregroundAndBackgroundAreSpansWithTheirState() {
    ObservabilityService service = quiet();
    service.afterIdentify(Attributes.builder().put("user", "u").build(), "u");
    service.desktopSignals().background();
    service.desktopSignals().foreground();
    List<SpanData> spans = telemetry.finishedSpans(service);
    assertThat(names(spans), contains("app_background", "app_foreground"));
    assertEquals("background", spans.get(0).getAttributes().get(ObservabilityService.EVENT_LIFECYCLE_STATE));
    assertEquals("foreground", spans.get(1).getAttributes().get(ObservabilityService.EVENT_LIFECYCLE_STATE));
    assertEquals("u", spans.get(1).getAttributes().get(AttributeKey.stringKey("user")));
  }

  @Test
  public void inTheBackgroundTheSessionTimesOut() {
    ObservabilityService service = quiet();
    String first = service.getSessionId();
    service.desktopSignals().background();
    telemetry.clock.addAndGet(TestTelemetry.BACKGROUND_TIMEOUT);
    assertNotEquals(first, service.getSessionId());
  }

  @Test
  public void aScreenViewNamesThePreviousScreen() {
    ObservabilityService service = quiet();
    service.desktopSignals().screenShown("Main", "app.MainFrame", "app.MainFrame#frame0");
    service.desktopSignals().screenShown("Main", "app.MainFrame", "app.MainFrame#frame0");
    service.trackScreenView("Settings", null, null, "prefs", Attributes.builder().put("event.name", "spoof")
        .put("tab", "general").build());
    List<SpanData> views = telemetry.spansNamed(service, "screen_view");
    assertEquals("showing the current screen again is not a view", 2, views.size());
    Attributes main = views.get(0).getAttributes();
    assertEquals("Main", main.get(ObservabilityService.EVENT_NAME));
    assertEquals("app.MainFrame", main.get(ObservabilityService.EVENT_SCREEN_CLASS));
    assertEquals("app.MainFrame#frame0", main.get(ObservabilityService.EVENT_SCREEN_ID));
    assertNull(main.get(ObservabilityService.EVENT_PREVIOUS_SCREEN));
    Attributes settings = views.get(1).getAttributes();
    assertEquals("Settings", settings.get(ObservabilityService.EVENT_NAME));
    assertEquals("Main", settings.get(ObservabilityService.EVENT_PREVIOUS_SCREEN));
    assertEquals("prefs", settings.get(ObservabilityService.EVENT_CATEGORY));
    assertEquals("general", settings.get(AttributeKey.stringKey("tab")));
    assertNull(settings.get(ObservabilityService.EVENT_SCREEN_ID));
  }

  @Test
  public void aNewSessionForgetsThePreviousScreens() {
    ObservabilityService service = quiet();
    service.trackScreenView("Main", null, null, null, Attributes.empty());
    telemetry.clock.addAndGet(Constants.SESSION_MAX_LIFETIME_MILLIS);
    service.trackScreenView("Main", null, null, null, Attributes.empty());
    List<SpanData> views = telemetry.spansNamed(service, "screen_view");
    assertEquals(2, views.size());
    assertNull(views.get(1).getAttributes().get(ObservabilityService.EVENT_PREVIOUS_SCREEN));
  }

  @Test
  public void aClickCarriesTheComponentAndTheScreen() {
    ObservabilityService service = quiet();
    service.afterIdentify(Attributes.builder().put("user", "u").build(), "u");
    service.trackScreenView("Main", "app.MainFrame", "main", null, Attributes.empty());
    service.recordClick(new Click("JButton", "javax.swing.JButton", "save", "Save",
        "JFrame/JButton#save", 10, 20, 1_000, 1_080));
    service.recordClick(new Click("JPanel", "javax.swing.JPanel", null, null, "JPanel", 1, 2,
        5_000, 5_000));
    List<SpanData> clicks = telemetry.spansNamed(service, "click");
    Attributes save = clicks.get(0).getAttributes();
    assertEquals("click", save.get(ObservabilityService.EVENT_TYPE));
    assertEquals("JButton", save.get(ObservabilityService.EVENT_TAG));
    assertEquals("javax.swing.JButton", save.get(ObservabilityService.EVENT_CLASSNAME));
    assertEquals("save", save.get(ObservabilityService.EVENT_ID));
    assertEquals("Save", save.get(ObservabilityService.EVENT_TEXT));
    assertEquals("JFrame/JButton#save", save.get(ObservabilityService.EVENT_XPATH));
    assertEquals("main", save.get(ObservabilityService.EVENT_SCREEN_ID));
    assertEquals("Main", save.get(ObservabilityService.EVENT_SCREEN_NAME));
    assertEquals(Long.valueOf(10), save.get(ObservabilityService.EVENT_X));
    assertEquals(Long.valueOf(20), save.get(ObservabilityService.EVENT_Y));
    assertEquals("u", save.get(AttributeKey.stringKey("user")));
    assertEquals(TimeUnit.MILLISECONDS.toNanos(1_000), clicks.get(0).getStartEpochNanos());
    assertEquals(TimeUnit.MILLISECONDS.toNanos(1_080), clicks.get(0).getEndEpochNanos());
    assertNull(clicks.get(1).getAttributes().get(ObservabilityService.EVENT_ID));
    assertNull(clicks.get(1).getAttributes().get(ObservabilityService.EVENT_TEXT));
  }

  @Test
  public void eachSpanHasItsOwnSwitch() {
    ObservabilityService service = service(TestTelemetry.quietOptions().analytics(ObservabilityOptions.Analytics
        .builder().taps(false).screenViews(false).appLifecycle(false).appLaunch(false).build()).build());
    service.desktopSignals().foreground();
    service.desktopSignals().background();
    service.desktopSignals().screenShown("Main", null, null);
    service.recordClick(new Click("t", "c", null, null, "p", 0, 0, 0, 0));
    assertThat(telemetry.finishedSpans(service), empty());
    assertEquals("the screen is still tracked for clicks", "Main", service.currentScreenName());
  }

  @Test
  public void turningSpansOffTurnsTheseOff() {
    ObservabilityService service = service(TestTelemetry.quietOptions()
        .tracesApi(ObservabilityOptions.TracesApi.builder().includeSpans(false).build()).build());
    service.desktopSignals().foreground();
    service.desktopSignals().screenShown("Main", null, null);
    service.recordClick(new Click("t", "c", null, null, "p", 0, 0, 0, 0));
    assertThat(telemetry.finishedSpans(service), empty());
  }

  @Test
  public void ldObserveTracksScreenViews() {
    ObservabilityRuntime.setFactoryForTest(telemetry.factory());
    LDObserve.trackScreenView(null, null, null, null, null);
    LDObserve.trackScreenView("before start", null, null, null, null);
    LDObserve.init(TestTelemetry.CREDENTIAL, TestTelemetry.quietOptions().build());
    LDObserve.trackScreenView("Cart", "app.Cart", "cart", "shop", Collections.singletonMap("items", 2));
    ObservabilityService running = ObservabilityRuntime.current();
    SpanData view = telemetry.spansNamed(running, "screen_view").get(0);
    assertEquals("Cart", view.getAttributes().get(ObservabilityService.EVENT_NAME));
    assertEquals(Long.valueOf(2), view.getAttributes().get(AttributeKey.longKey("items")));
  }

  @Test
  public void theClickSignalBecomesAClickSpan() {
    ObservabilityService service = quiet();
    service.desktopSignals().click(new Click("JButton", "javax.swing.JButton", null, null,
        "JButton", 0, 0, 1, 2));
    assertThat(names(telemetry.finishedSpans(service)), contains("click"));
  }

  @Test
  public void clicksAloneStartTheInstrumentation() {
    ObservabilityService service = service(TestTelemetry.quietOptions().instrumentations(
        ObservabilityOptions.Instrumentations.builder().crashReporting(false).screens(false).userTaps(true).build())
        .build());
    service.shutdown();
    this.service = null;
  }

  @Test
  public void aNullScreenNameIsIgnored() {
    ObservabilityRuntime.setFactoryForTest(telemetry.factory());
    LDObserve.init(TestTelemetry.CREDENTIAL, TestTelemetry.quietOptions().build());
    LDObserve.trackScreenView(null, null, null, null, null);
    assertThat(telemetry.finishedSpans(ObservabilityRuntime.current()), empty());
  }

  @Test
  public void theServiceStartsAndStopsTheInstrumentation() {
    ObservabilityService service = service(TestTelemetry.quietOptions()
        .instrumentations(ObservabilityOptions.Instrumentations.builder().crashReporting(false).build()).build());
    service.shutdown();
    this.service = null;
  }

  // -- the instrumentation --

  private static final class Recorded implements ToolkitInstrumentation.Signals {
    final List<String> events = new ArrayList<>();
    final List<Click> clicks = new ArrayList<>();

    @Override
    public synchronized void foreground() {
      events.add("foreground");
    }

    @Override
    public synchronized void background() {
      events.add("background");
    }

    @Override
    public synchronized void startedInBackground() {
      events.add("started in background");
    }

    @Override
    public synchronized void screenShown(String name, String screenClass, String screenId) {
      events.add("screen " + name + " " + screenClass + " " + screenId);
    }

    @Override
    public synchronized void click(Click click) {
      clicks.add(click);
    }

    synchronized List<String> events() {
      return new ArrayList<>(events);
    }
  }

  private DesktopInstrumentation instrumentation(Recorded recorded, boolean windows, boolean clicks,
      AtomicBoolean awtInUse, AtomicReference<Window> active) {
    ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    DesktopInstrumentation instrumentation = new DesktopInstrumentation(recorded, windows, clicks, true, LDLogger.none(),
        scheduler, awtInUse::get, active::get, DesktopInstrumentation.TOOLKIT);
    instrumentations.add(instrumentation);
    return instrumentation;
  }

  private static boolean registered(AWTEventListener listener) {
    for (AWTEventListener l : Toolkit.getDefaultToolkit().getAWTEventListeners()) {
      if (l == listener || (l instanceof AWTEventListenerProxy && ((AWTEventListenerProxy) l).getListener() == listener)) {
        return true;
      }
    }
    return false;
  }

  @Test
  public void itWaitsForAwtBeforeAttaching() throws Exception {
    assumeDisplay();
    AtomicBoolean awtInUse = new AtomicBoolean(false);
    Recorded recorded = new Recorded();
    DesktopInstrumentation instrumentation = instrumentation(recorded, true, true, awtInUse,
        new AtomicReference<>());
    instrumentation.start();
    assertFalse(registered(instrumentation));
    Thread.sleep(DesktopInstrumentation.ATTACH_POLL_MILLIS + 300);
    assertFalse("still waiting", registered(instrumentation));
    awtInUse.set(true);
    long deadline = System.currentTimeMillis() + 5_000;
    while (!registered(instrumentation) && System.currentTimeMillis() < deadline) {
      Thread.sleep(50);
    }
    assertTrue(registered(instrumentation));
    instrumentation.start();
    assertThat("no window yet, but starting is not a transition", recorded.events(), empty());
    // Still no window after the startup grace period: the application started in the background.
    instrumentation.checkStartedInBackground();
    instrumentation.checkStartedInBackground();
    assertThat(recorded.events(), contains("started in background"));
    instrumentation.stop();
    assertFalse(registered(instrumentation));
  }

  @Test
  public void attachingWithAnActiveWindowShowsItsScreen() {
    assumeDisplay();
    JFrame frame = window(new JFrame("Main window"));
    frame.setName("frame-main");
    Recorded recorded = new Recorded();
    DesktopInstrumentation instrumentation = instrumentation(recorded, true, false, new AtomicBoolean(true),
        new AtomicReference<>(frame));
    instrumentation.start();
    assertThat(recorded.events(), contains("screen Main window javax.swing.JFrame javax.swing.JFrame#frame-main"));
  }

  @Test
  public void nothingToWatchStartsNothing() {
    Recorded recorded = new Recorded();
    DesktopInstrumentation instrumentation = instrumentation(recorded, false, false, new AtomicBoolean(true),
        new AtomicReference<>());
    instrumentation.start();
    assertFalse(registered(instrumentation));
    assertThat(recorded.events(), empty());
  }

  @Test
  public void aRefusedListenerStopsTheAttempt() {
    Recorded recorded = new Recorded();
    AtomicBoolean removed = new AtomicBoolean();
    DesktopInstrumentation instrumentation = new DesktopInstrumentation(recorded, true, true, true, LDLogger.none(),
        Executors.newSingleThreadScheduledExecutor(), () -> true, () -> null, new DesktopInstrumentation.Listeners() {
          @Override
          public void add(AWTEventListener listener, long mask) {
            throw new java.awt.HeadlessException();
          }

          @Override
          public void remove(AWTEventListener listener) {
            removed.set(true);
          }
        });
    instrumentations.add(instrumentation);
    instrumentation.start();
    instrumentation.start();
    instrumentation.stop();
    assertThat(recorded.events(), empty());
    assertFalse("never attached, so never removed", removed.get());
  }

  @Test
  public void aFailingRemovalOnStopIsIgnored() {
    DesktopInstrumentation instrumentation = new DesktopInstrumentation(new Recorded(), false, true, true,
        LDLogger.none(), Executors.newSingleThreadScheduledExecutor(), () -> true, () -> null,
        new DesktopInstrumentation.Listeners() {
          @Override
          public void add(AWTEventListener listener, long mask) {
            assertEquals(AWTEvent.MOUSE_EVENT_MASK, mask);
          }

          @Override
          public void remove(AWTEventListener listener) {
            throw new IllegalStateException("already gone");
          }
        });
    instrumentation.start();
    instrumentation.stop();
  }

  @Test
  public void windowsDecideForegroundAndScreens() throws Exception {
    assumeDisplay();
    JFrame main = window(new JFrame("Main"));
    JDialog dialog = window(new JDialog(main, "  "));
    JWindow plain = window(new JWindow());
    AtomicReference<Window> active = new AtomicReference<>(main);
    Recorded recorded = new Recorded();
    DesktopInstrumentation instrumentation = instrumentation(recorded, true, false, new AtomicBoolean(true), active);
    instrumentation.start();

    // Moving between the application's own windows is not a background.
    instrumentation.eventDispatched(new WindowEvent(main, WindowEvent.WINDOW_DEACTIVATED, dialog, 0, 0));
    instrumentation.eventDispatched(new WindowEvent(dialog, WindowEvent.WINDOW_ACTIVATED, main, 0, 0));
    instrumentation.eventDispatched(new WindowEvent(plain, WindowEvent.WINDOW_ACTIVATED, dialog, 0, 0));
    // Focus to nothing, then straight back: some window managers do that between two windows.
    instrumentation.eventDispatched(new WindowEvent(plain, WindowEvent.WINDOW_DEACTIVATED, null, 0, 0));
    instrumentation.eventDispatched(new WindowEvent(main, WindowEvent.WINDOW_ACTIVATED, null, 0, 0));
    Thread.sleep(DesktopInstrumentation.BACKGROUND_DELAY_MILLIS * 2);
    // Focus leaves the application.
    active.set(null);
    instrumentation.eventDispatched(new WindowEvent(main, WindowEvent.WINDOW_DEACTIVATED, null, 0, 0));
    long deadline = System.currentTimeMillis() + 5_000;
    while (!recorded.events().contains("background") && System.currentTimeMillis() < deadline) {
      Thread.sleep(20);
    }
    instrumentation.eventDispatched(new WindowEvent(main, WindowEvent.WINDOW_ACTIVATED, null, 0, 0));
    instrumentation.eventDispatched(new WindowEvent(main, WindowEvent.WINDOW_OPENED, null, 0, 0));

    String mainScreen = "screen Main javax.swing.JFrame javax.swing.JFrame#" + main.getName();
    assertThat(recorded.events(), contains(
        mainScreen,
        "screen JDialog javax.swing.JDialog javax.swing.JDialog#" + dialog.getName(),
        "screen JWindow javax.swing.JWindow javax.swing.JWindow#" + plain.getName(),
        mainScreen,
        "background",
        "foreground",
        mainScreen));
  }

  @Test
  public void aWindowWithoutANameIsKnownByItsClass() {
    assumeDisplay();
    JFrame frame = window(new JFrame((String) null));
    frame.setName(null);
    Recorded recorded = new Recorded();
    DesktopInstrumentation instrumentation = instrumentation(recorded, true, false, new AtomicBoolean(true),
        new AtomicReference<>(frame));
    instrumentation.start();
    assertThat(recorded.events(), contains("screen JFrame javax.swing.JFrame javax.swing.JFrame"));
  }

  @Test
  public void aWindowOpeningDuringStartupIsNoTransition() throws Exception {
    assumeDisplay();
    JFrame frame = window(new JFrame("Main"));
    AtomicReference<Window> active = new AtomicReference<>();
    Recorded recorded = new Recorded();
    DesktopInstrumentation instrumentation = instrumentation(recorded, true, false, new AtomicBoolean(true), active);
    instrumentation.start();
    // The login dialog has closed and the main window opens a moment later.
    active.set(frame);
    instrumentation.eventDispatched(new WindowEvent(frame, WindowEvent.WINDOW_ACTIVATED, null, 0, 0));
    instrumentation.checkStartedInBackground();
    assertThat(recorded.events(), contains("screen Main javax.swing.JFrame javax.swing.JFrame#" + frame.getName()));
  }

  @Test
  public void theStartupCheckRunsOnItsOwn() throws Exception {
    Recorded recorded = new Recorded();
    DesktopInstrumentation instrumentation = instrumentation(recorded, true, false, new AtomicBoolean(true),
        new AtomicReference<>());
    instrumentation.start();
    long deadline = System.currentTimeMillis() + DesktopInstrumentation.STARTUP_DELAY_MILLIS + 5_000;
    while (recorded.events().isEmpty() && System.currentTimeMillis() < deadline) {
      Thread.sleep(50);
    }
    assertThat(recorded.events(), contains("started in background"));
    instrumentation.stop();
    instrumentation.checkStartedInBackground();
    assertThat(recorded.events(), contains("started in background"));
  }

  @Test
  public void withoutRecordTextNoTitleOrTextIsRecorded() {
    assumeDisplay();
    JFrame frame = window(new JFrame("Patient: Jane Doe"));
    Recorded recorded = new Recorded();
    DesktopInstrumentation instrumentation = new DesktopInstrumentation(recorded, true, true, false,
        LDLogger.none(), Executors.newSingleThreadScheduledExecutor(), () -> true, () -> frame,
        DesktopInstrumentation.TOOLKIT);
    instrumentations.add(instrumentation);
    instrumentation.start();
    instrumentation.eventDispatched(mouse(new JButton("Delete jane@example.com"), MouseEvent.MOUSE_CLICKED, 1));
    assertThat(recorded.events(), contains("screen JFrame javax.swing.JFrame javax.swing.JFrame#" + frame.getName()));
    assertNull(recorded.clicks.get(0).text);
    assertEquals("JButton", recorded.clicks.get(0).tag);
  }

  @Test
  public void aClickInApachePivotNamesThePivotComponent() throws Exception {
    org.apache.pivot.wtk.ApplicationContext.DisplayHost host =
        PivotFixture.onEdt(org.apache.pivot.wtk.ApplicationContext.DisplayHost::new);
    PivotFixture.onEdt(() -> {
      org.apache.pivot.wtk.Panel content = PivotFixture.content(host, 200, 100);
      org.apache.pivot.wtk.PushButton save = PivotFixture.at(content, new org.apache.pivot.wtk.PushButton("Save"),
          0, 10, 50, 20);
      save.setName("save");
      return null;
    });
    Recorded recorded = new Recorded();
    DesktopInstrumentation instrumentation = new DesktopInstrumentation(recorded, false, true, true, LDLogger.none(),
        Executors.newSingleThreadScheduledExecutor(), () -> true, () -> null, DesktopInstrumentation.TOOLKIT);
    instrumentations.add(instrumentation);
    PivotFixture.onEdt(() -> {
      instrumentation.eventDispatched(mouse(host, MouseEvent.MOUSE_CLICKED, 1));
      return null;
    });
    Click click = recorded.clicks.get(0);
    assertEquals("PushButton", click.tag);
    assertEquals("org.apache.pivot.wtk.PushButton", click.classname);
    assertEquals("save", click.id);
    assertEquals("Save", click.text);
    assertEquals("DisplayHost/Display/Window/Panel/PushButton#save", click.xpath);

    PivotFixture.onEdt(() -> {
      instrumentation.eventDispatched(new MouseEvent(host, MouseEvent.MOUSE_CLICKED, 2, 0, 190, 90, 190, 90, 1,
          false, MouseEvent.BUTTON1));
      return null;
    });
    assertEquals("nothing there but the panel", "Panel", recorded.clicks.get(1).tag);
    assertNull(recorded.clicks.get(1).text);

    PivotFixture.onEdt(() -> {
      instrumentation.eventDispatched(new MouseEvent(host, MouseEvent.MOUSE_CLICKED, 3, 0, 900, 900, 900, 900, 1,
          false, MouseEvent.BUTTON1));
      return null;
    });
    assertEquals("outside the display, the display", "Display", recorded.clicks.get(2).tag);

    Recorded quiet = new Recorded();
    DesktopInstrumentation noText = new DesktopInstrumentation(quiet, false, true, false, LDLogger.none(),
        Executors.newSingleThreadScheduledExecutor(), () -> true, () -> null, DesktopInstrumentation.TOOLKIT);
    instrumentations.add(noText);
    PivotFixture.onEdt(() -> {
      noText.eventDispatched(mouse(host, MouseEvent.MOUSE_CLICKED, 4));
      return null;
    });
    assertEquals("PushButton", quiet.clicks.get(0).tag);
    assertNull("without recordText, no text", quiet.clicks.get(0).text);
  }

  @Test
  public void aPivotTheInstrumentationCannotReadReportsItsHost() throws Exception {
    org.apache.pivot.wtk.ApplicationContext.DisplayHost broken = PivotFixture.onEdt(
        () -> new org.apache.pivot.wtk.ApplicationContext.DisplayHost() {
          private static final long serialVersionUID = 1L;

          @Override
          public org.apache.pivot.wtk.Display getDisplay() {
            throw new IllegalStateException("not this Pivot");
          }
        });
    Recorded recorded = new Recorded();
    DesktopInstrumentation instrumentation = new DesktopInstrumentation(recorded, false, true, false, LDLogger.none(),
        Executors.newSingleThreadScheduledExecutor(), () -> true, () -> null, DesktopInstrumentation.TOOLKIT);
    instrumentations.add(instrumentation);
    instrumentation.eventDispatched(mouse(broken, MouseEvent.MOUSE_CLICKED, 1));
    assertEquals("DisplayHost", recorded.clicks.get(0).tag);
  }

  @Test
  public void theProductionFactoryBuildsAnInstrumentation() {
    DesktopInstrumentation instrumentation = DesktopInstrumentation.create(new Recorded(), false, false, false,
        LDLogger.none());
    instrumentations.add(instrumentation);
    instrumentation.start();
  }

  @Test
  public void onlyNamesTheApplicationGaveAreRecorded() {
    JButton named = new JButton();
    named.setName("save");
    assertEquals("save", DesktopInstrumentation.name(named));
    assertNull(DesktopInstrumentation.name(new JButton()));
    JPanel internal = new JPanel();
    internal.setName("null.contentPane");
    assertNull("Swing's own panes", DesktopInstrumentation.name(internal));
    Button awt = new Button();
    awt.setName("button7");
    assertNull("AWT names its components itself", DesktopInstrumentation.name(awt));
  }

  @Test
  public void startingInTheBackgroundIsNoSpanButStartsTheTimeout() {
    ObservabilityService service = quiet();
    String first = service.getSessionId();
    service.desktopSignals().startedInBackground();
    telemetry.clock.addAndGet(TestTelemetry.BACKGROUND_TIMEOUT);
    assertNotEquals(first, service.getSessionId());
    assertThat(telemetry.finishedSpans(service), empty());
  }

  @Test
  public void theBackgroundCheckDoesNothingOnceStopped() {
    Recorded recorded = new Recorded();
    DesktopInstrumentation instrumentation = instrumentation(recorded, true, false, new AtomicBoolean(false),
        new AtomicReference<>());
    instrumentation.stop();
    instrumentation.checkBackground();
    assertThat(recorded.events(), empty());
  }

  @Test
  public void aClickIsTimedFromItsPress() {
    Recorded recorded = new Recorded();
    DesktopInstrumentation instrumentation = instrumentation(recorded, false, true, new AtomicBoolean(false),
        new AtomicReference<>());
    JPanel root = new JPanel();
    JPanel form = new JPanel();
    form.setName("form");
    JButton save = new JButton("Save");
    save.setName("save");
    root.add(form);
    form.add(save);

    instrumentation.eventDispatched(mouse(save, MouseEvent.MOUSE_PRESSED, 1_000));
    instrumentation.eventDispatched(mouse(save, MouseEvent.MOUSE_RELEASED, 1_050));
    instrumentation.eventDispatched(mouse(save, MouseEvent.MOUSE_CLICKED, 1_060));
    // A click without a press of its own, and one on a component pressed earlier.
    instrumentation.eventDispatched(mouse(root, MouseEvent.MOUSE_CLICKED, 2_000));
    instrumentation.eventDispatched(mouse(root, MouseEvent.MOUSE_PRESSED, 3_000));
    instrumentation.eventDispatched(mouse(save, MouseEvent.MOUSE_CLICKED, 3_010));
    // A press stamped after its click, by a clock that moved.
    instrumentation.eventDispatched(mouse(save, MouseEvent.MOUSE_PRESSED, 5_000));
    instrumentation.eventDispatched(mouse(save, MouseEvent.MOUSE_CLICKED, 4_000));

    assertEquals(4, recorded.clicks.size());
    Click click = recorded.clicks.get(0);
    assertEquals("JButton", click.tag);
    assertEquals("javax.swing.JButton", click.classname);
    assertEquals("save", click.id);
    assertEquals("Save", click.text);
    assertEquals("JPanel/JPanel#form/JButton#save", click.xpath);
    assertEquals(110, click.x);
    assertEquals(220, click.y);
    assertEquals(1_000, click.startMillis);
    assertEquals(1_060, click.endMillis);
    assertEquals(2_000, recorded.clicks.get(1).startMillis);
    assertEquals(3_010, recorded.clicks.get(2).startMillis);
    assertEquals(4_000, recorded.clicks.get(3).startMillis);
  }

  @Test
  public void anErrorWhileWatchingIsSwallowed() {
    DesktopInstrumentation instrumentation = new DesktopInstrumentation(new ToolkitInstrumentation.Signals() {
      @Override
      public void foreground() {
      }

      @Override
      public void background() {
      }

      @Override
      public void startedInBackground() {
      }

      @Override
      public void screenShown(String name, String screenClass, String screenId) {
      }

      @Override
      public void click(Click click) {
        throw new IllegalStateException("broken");
      }
    }, false, true, true, LDLogger.none(), Executors.newSingleThreadScheduledExecutor(), () -> false, () -> null,
        DesktopInstrumentation.TOOLKIT);
    instrumentations.add(instrumentation);
    instrumentation.eventDispatched(mouse(new JButton(), MouseEvent.MOUSE_CLICKED, 1));
    instrumentation.eventDispatched(new java.awt.event.ActionEvent(new JButton(), 0, "ignored"));
  }

  private static MouseEvent mouse(Component component, int id, long when) {
    return new MouseEvent(component, id, when, 0, 10, 20, 110, 220, 1, false, MouseEvent.BUTTON1);
  }

  @Test
  public void onlyButtonAndLabelTextIsRecorded() {
    assertEquals("OK", DesktopInstrumentation.text(new JButton("OK")));
    assertEquals("Name", DesktopInstrumentation.text(new JLabel("Name")));
    assertEquals("Go", DesktopInstrumentation.text(new Button("Go")));
    assertEquals("Hi", DesktopInstrumentation.text(new Label("Hi")));
    assertNull(DesktopInstrumentation.text(new JPasswordField("secret")));
    assertNull(DesktopInstrumentation.text(new JButton("  ")));
    assertNull(DesktopInstrumentation.text(new JButton()));
    StringBuilder longText = new StringBuilder();
    for (int i = 0; i < 200; i++) {
      longText.append('x');
    }
    assertEquals(DesktopInstrumentation.MAX_TEXT_LENGTH,
        DesktopInstrumentation.text(new JButton(longText.toString())).length());
  }

  @Test
  public void pathsStopAtTheWindowAndADepthLimit() {
    assumeDisplay();
    JFrame frame = window(new JFrame("f"));
    JButton button = new JButton("b");
    frame.getContentPane().add(button);
    assertTrue(DesktopInstrumentation.path(button), DesktopInstrumentation.path(button).startsWith("JFrame/"));
    JPanel deepest = new JPanel();
    JPanel parent = deepest;
    for (int i = 0; i < 40; i++) {
      JPanel next = new JPanel();
      next.add(parent);
      parent = next;
    }
    assertEquals(DesktopInstrumentation.MAX_PATH_DEPTH, DesktopInstrumentation.path(deepest).split("/").length);
  }

  @Test
  public void anonymousClassesAreNamedByTheirSuperclass() {
    assertEquals("JButton", DesktopInstrumentation.simpleName(new JButton() { }.getClass()));
    assertEquals("Object", DesktopInstrumentation.simpleName(new Object() { }.getClass()));
  }

  @Test
  public void awtIsDetectedFromItsThreadNames() {
    assertTrue(DesktopInstrumentation.anyAwtThread(java.util.Arrays.asList("main", "AWT-EventQueue-0")));
    assertTrue(DesktopInstrumentation.anyAwtThread(Collections.singletonList("AWT-AppKit")));
    assertTrue(DesktopInstrumentation.anyAwtThread(Collections.singletonList("AWT-Windows")));
    assertTrue(DesktopInstrumentation.anyAwtThread(Collections.singletonList("AWT-XAWT")));
    assertFalse(DesktopInstrumentation.anyAwtThread(java.util.Arrays.asList("main", "AWT-Shutdown", "worker")));
    // This JVM's own threads are read without error, whatever they are.
    DesktopInstrumentation.awtThreadsRunning();
    Thread thread = DesktopInstrumentation.daemonThread(() -> { });
    assertTrue(thread.isDaemon());
    assertEquals("LaunchDarkly-observability-desktop", thread.getName());
  }

  @Test
  public void theBackgroundCheckKeepsTheForegroundWhileAWindowIsActive() {
    assumeDisplay();
    JFrame frame = window(new JFrame("Main"));
    AtomicReference<Window> active = new AtomicReference<>(frame);
    Recorded recorded = new Recorded();
    DesktopInstrumentation instrumentation = instrumentation(recorded, true, false, new AtomicBoolean(true), active);
    instrumentation.start();
    instrumentation.checkBackground();
    active.set(null);
    instrumentation.stop();
    instrumentation.checkBackground();
    assertEquals("only the first screen", 1, recorded.events().size());
  }

  @Test
  public void anActiveAwtWindowIsReportedOnlyOnceAttached() {
    AtomicReference<Window> active = new AtomicReference<>();
    AtomicBoolean inUse = new AtomicBoolean(false);
    DesktopInstrumentation instrumentation = new DesktopInstrumentation(new Recorded(), true, false, false,
        LDLogger.none(), Executors.newSingleThreadScheduledExecutor(), inUse::get, active::get,
        new DesktopInstrumentation.Listeners() {
          @Override
          public void add(AWTEventListener listener, long mask) {}

          @Override
          public void remove(AWTEventListener listener) {}
        });
    instrumentations.add(instrumentation);
    active.set(new Window(null));
    assertFalse("not attached, so AWT is not asked", instrumentation.hasActiveWindow());
    inUse.set(true);
    instrumentation.start();
    assertTrue(instrumentation.hasActiveWindow());
    active.set(null);
    assertFalse(instrumentation.hasActiveWindow());
  }
}
