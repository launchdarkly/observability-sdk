package com.launchdarkly.observability.internal;

import com.launchdarkly.logging.LDLogger;

import io.opentelemetry.api.common.Attributes;

import org.junit.After;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

/** The listeners other instrumentation hears the service through, and how the toolkits' signals join. */
public class ToolkitTest {
  private final TestTelemetry telemetry = new TestTelemetry();
  private ObservabilityService service;

  @After
  public void tearDown() {
    if (service != null) {
      service.shutdown();
    }
    ObservabilityRuntime.resetForTest();
  }

  private ObservabilityService quiet() {
    service = telemetry.service(TestTelemetry.quietOptions().build());
    return service;
  }

  /** Writes down every call, in order. */
  private static final class Heard implements ObservabilityListener {
    final List<String> calls = Collections.synchronizedList(new ArrayList<>());

    @Override
    public void foreground() {
      calls.add("foreground");
    }

    @Override
    public void background() {
      calls.add("background");
    }

    @Override
    public void screenShown(String name, String screenClass, String screenId) {
      calls.add("screen " + name + " " + screenClass + " " + screenId);
    }

    @Override
    public void click(Click click) {
      calls.add("click " + click.getTag() + " " + click.getXpath() + " " + click.getText() + " "
          + click.getEndMillis());
    }

    @Override
    public void track(String key, Double metricValue, Attributes data) {
      calls.add("track " + key + " " + metricValue + " " + data.size());
    }

    @Override
    public void identified(java.util.Map<String, String> contextKeys, String canonicalKey) {
      calls.add("identified " + contextKeys + " " + canonicalKey);
    }

    @Override
    public void stopped() {
      calls.add("stopped");
    }
  }

  // -- listeners --

  @Test
  public void aListenerHearsEverySignal() {
    ObservabilityService service = quiet();
    Heard heard = new Heard();
    service.addListener(heard);
    service.desktopSignals().background();
    service.desktopSignals().foreground();
    service.desktopSignals().screenShown("Main", "app.Main", "app.Main#main");
    service.desktopSignals().screenShown("Main", "app.Main", "app.Main#main");
    service.desktopSignals().click(new Click("JButton", "javax.swing.JButton", null, "Save", "JFrame/JButton", 1, 2,
        3, 4));
    service.track("bought", 2.5, Attributes.builder().put("item", "hat").build(), null);
    service.afterIdentify(Attributes.builder().put("user", "u").put("org", "o").build(), "org:o:user:u");
    service.shutdown();
    this.service = null;
    assertThat(heard.calls, contains("background", "foreground", "screen Main app.Main app.Main#main",
        "click JButton JFrame/JButton Save 4", "track bought 2.5 1", "identified {org=o, user=u} org:o:user:u",
        "stopped"));
  }

  @Test
  public void aListenerHearsSignalsWhoseSpansAreOff() {
    service = telemetry.service(TestTelemetry.quietOptions().tracesApi(
        com.launchdarkly.observability.ObservabilityOptions.TracesApi.builder().includeSpans(false).build()).build());
    Heard heard = new Heard();
    service.addListener(heard);
    service.track("bought", null, Attributes.empty(), null);
    service.trackScreenView("Main", null, null, null, Attributes.empty());
    assertThat(heard.calls, contains("track bought null 0", "screen Main null null"));
    assertThat(telemetry.finishedSpans(service), empty());
  }

  @Test
  public void aThrowingListenerIsIgnoredAndARemovedOneHearsNothing() {
    ObservabilityService service = quiet();
    Heard removed = new Heard();
    Heard kept = new Heard();
    service.addListener(new ObservabilityListener() {
      @Override
      public void foreground() {
        throw new IllegalStateException("boom");
      }
    });
    service.addListener(removed);
    service.addListener(kept);
    service.removeListener(removed);
    service.desktopSignals().foreground();
    assertThat(removed.calls, empty());
    assertThat(kept.calls, contains("foreground"));
  }

  @Test
  public void theDefaultListenerDoesNothing() {
    ObservabilityListener listener = new ObservabilityListener() {};
    listener.foreground();
    listener.background();
    listener.screenShown("a", null, null);
    listener.click(new Click("t", "c", null, null, "p", 0, 0, 0, 0));
    listener.track("k", null, Attributes.empty());
    listener.identified(Collections.emptyMap(), "k");
    listener.stopped();
  }

  @Test
  public void aClickNamesItsComponent() {
    Click click = new Click("JButton", "javax.swing.JButton", "save", null, "JButton", 0, 0, 0, 0);
    assertEquals("javax.swing.JButton", click.getClassname());
    assertEquals("save", click.getId());
  }

  @Test
  public void theServiceHandsOutWhatItRunsWith() {
    ObservabilityService service = quiet();
    assertNotNull(service.getOptions());
    assertSame(telemetry.logger, service.getLogger());
    assertEquals(TestTelemetry.CREDENTIAL, service.getCredential());
    assertNotNull(service.getResource());
  }

  // -- joining toolkits --

  /** A toolkit whose active window a test sets. */
  private static final class FakeToolkit implements ToolkitInstrumentation {
    volatile boolean active;

    @Override
    public void start() {}

    @Override
    public void stop() {}

    @Override
    public boolean hasActiveWindow() {
      return active;
    }
  }

  /** Writes down the joined signals. */
  private static final class Joined implements ToolkitInstrumentation.Signals {
    final List<String> calls = new ArrayList<>();

    @Override
    public void foreground() {
      calls.add("foreground");
    }

    @Override
    public void background() {
      calls.add("background");
    }

    @Override
    public void startedInBackground() {
      calls.add("startedInBackground");
    }

    @Override
    public void screenShown(String name, String screenClass, String screenId) {
      calls.add("screen " + name);
    }

    @Override
    public void click(Click click) {
      calls.add("click");
    }
  }

  @Test
  public void movingBetweenToolkitsIsNotTheBackground() {
    Joined joined = new Joined();
    ToolkitSignals signals = new ToolkitSignals(joined);
    FakeToolkit swing = new FakeToolkit();
    FakeToolkit fx = new FakeToolkit();
    signals.add(swing);
    signals.add(fx);

    // From a Swing window to a JavaFX one: JavaFX gains focus, then Swing reports it has none.
    fx.active = true;
    signals.foreground();
    signals.background();
    assertThat("already in the foreground, and JavaFX has a window", joined.calls, empty());

    // Leaving the application: no toolkit has a window.
    fx.active = false;
    signals.background();
    signals.background();
    assertThat(joined.calls, contains("background"));

    signals.foreground();
    signals.foreground();
    assertThat(joined.calls, contains("background", "foreground"));
  }

  @Test
  public void startedInBackgroundCountsOnlyWhenNoToolkitHasAWindow() {
    Joined joined = new Joined();
    ToolkitSignals signals = new ToolkitSignals(joined);
    FakeToolkit swing = new FakeToolkit();
    signals.add(swing);
    swing.active = true;
    signals.startedInBackground();
    assertThat(joined.calls, empty());
    swing.active = false;
    signals.startedInBackground();
    signals.startedInBackground();
    assertThat(joined.calls, contains("startedInBackground"));
  }

  @Test
  public void screensAndClicksPassThrough() {
    Joined joined = new Joined();
    ToolkitSignals signals = new ToolkitSignals(joined);
    signals.screenShown("Main", null, null);
    signals.click(new Click("t", "c", null, null, "p", 0, 0, 0, 0));
    assertThat(joined.calls, contains("screen Main", "click"));
  }

  // -- JavaFX support --

  @Test
  public void withoutJavaFxThereIsNoInstrumentation() {
    assertNull(FxSupport.create("javafx.NotThere", FxSupport.INSTRUMENTATION_CLASS, new Joined(), true, true,
        false, LDLogger.none()));
  }

  @Test
  public void anInstrumentationThatCannotBeCreatedIsSkipped() {
    assertNull(FxSupport.create("java.lang.String", "com.example.NotThere", new Joined(), true, true, false,
        LDLogger.none()));
  }

  @Test
  public void withJavaFxThereIsAnInstrumentation() {
    FxAssume.assumeJavaFx();
    List<ToolkitInstrumentation> toolkits = new ArrayList<>();
    FxSupport.addTo(toolkits, new Joined(), true, true, false, LDLogger.none());
    FxSupport.addTo(toolkits, "javafx.NotThere", FxSupport.INSTRUMENTATION_CLASS, new Joined(), true, true, false,
        LDLogger.none());
    assertEquals("only the one JavaFX could create", 1, toolkits.size());
    toolkits.get(0).stop();
  }

  @Test
  public void theToolkitsShareTheirHelpers() {
    assertEquals("Object", ToolkitSupport.simpleName(new Object() {}.getClass()));
    assertNull(ToolkitSupport.visibleText(null));
    assertNull(ToolkitSupport.visibleText("  "));
    assertEquals("Save", ToolkitSupport.visibleText("Save"));
    assertEquals(ToolkitSupport.MAX_TEXT_LENGTH, ToolkitSupport.visibleText(new String(new char[200]).replace('\0', 'x'))
        .length());
  }

  @Test
  public void swtsLoopIsToldFromAStack() {
    StackTraceElement sleeping = new StackTraceElement("org.eclipse.swt.widgets.Display", "sleep", null, 1);
    StackTraceElement dispatching = new StackTraceElement("org.eclipse.swt.widgets.Display", "readAndDispatch", null, 1);
    StackTraceElement creating = new StackTraceElement("org.eclipse.swt.widgets.Display", "create", null, 1);
    StackTraceElement other = new StackTraceElement("com.example.App", "sleep", null, 1);
    org.junit.Assert.assertTrue(ToolkitSupport.runsSwtLoop(new StackTraceElement[] {other, sleeping}));
    org.junit.Assert.assertTrue(ToolkitSupport.runsSwtLoop(new StackTraceElement[] {dispatching}));
    org.junit.Assert.assertFalse(ToolkitSupport.runsSwtLoop(new StackTraceElement[] {creating, other}));
    org.junit.Assert.assertNull("no SWT in these tests", ToolkitSupport.swtThread());
  }
}
