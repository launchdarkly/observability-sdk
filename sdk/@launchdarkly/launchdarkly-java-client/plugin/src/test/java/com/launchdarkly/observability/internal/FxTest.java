package com.launchdarkly.observability.internal;

import com.launchdarkly.logging.LDLogger;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import javafx.application.Platform;
import javafx.event.Event;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.PickResult;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.stage.Popup;
import javafx.stage.Stage;
import javafx.stage.Window;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInRelativeOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasItem;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** The JavaFX instrumentation, against real JavaFX windows. Needs Java 11 or later and a display. */
public class FxTest {
  private final List<Window> windows = new ArrayList<>();
  private final List<FxInstrumentation> instrumentations = new ArrayList<>();

  @Before
  public void needJavaFx() {
    FxAssume.assumeJavaFx();
  }

  @After
  public void tearDown() throws Exception {
    instrumentations.forEach(FxInstrumentation::stop);
    onFx(() -> {
      windows.forEach(Window::hide);
      return null;
    });
  }

  /** Runs on the JavaFX thread and waits for the result. */
  static <T> T onFx(Callable<T> task) throws Exception {
    AtomicReference<T> result = new AtomicReference<>();
    AtomicReference<Exception> failure = new AtomicReference<>();
    CountDownLatch done = new CountDownLatch(1);
    Platform.runLater(() -> {
      try {
        result.set(task.call());
      } catch (Exception e) {
        failure.set(e);
      } finally {
        done.countDown();
      }
    });
    assertTrue("the JavaFX thread is stuck", done.await(10, TimeUnit.SECONDS));
    if (failure.get() != null) {
      throw failure.get();
    }
    return result.get();
  }

  /** Waits for whatever is queued on the JavaFX thread to run. */
  static void drainFx() throws Exception {
    onFx(() -> null);
  }

  /** Writes down what the instrumentation reports. */
  static class Recorded implements ToolkitInstrumentation.Signals {
    final List<String> calls = new java.util.concurrent.CopyOnWriteArrayList<>();
    final List<Click> clicks = new java.util.concurrent.CopyOnWriteArrayList<>();

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
      calls.add("screen " + name + " " + screenClass + " " + screenId);
    }

    @Override
    public void click(Click click) {
      clicks.add(click);
    }
  }

  private FxInstrumentation instrumentation(ToolkitInstrumentation.Signals signals, boolean windows, boolean clicks,
      boolean recordText, java.util.function.BooleanSupplier fxInUse) {
    FxInstrumentation fx = new FxInstrumentation(signals, windows, clicks, recordText, LDLogger.none(),
        Executors.newSingleThreadScheduledExecutor(), fxInUse);
    instrumentations.add(fx);
    return fx;
  }

  private FxInstrumentation attached(ToolkitInstrumentation.Signals signals, boolean recordText) throws Exception {
    FxInstrumentation fx = instrumentation(signals, true, true, recordText, () -> true);
    fx.start();
    drainFx();
    return fx;
  }

  private Stage stage(String title, javafx.scene.Parent root) throws Exception {
    return onFx(() -> {
      Stage stage = new Stage();
      stage.setTitle(title);
      stage.setScene(new Scene(root, 200, 120));
      stage.show();
      windows.add(stage);
      return stage;
    });
  }

  private static MouseEvent mouse(javafx.event.EventType<MouseEvent> type, Node picked) {
    return new MouseEvent(type, 5, 5, 105, 205, MouseButton.PRIMARY, 1, false, false, false, false, true, false,
        false, false, false, true, new PickResult(picked, 5, 5));
  }

  private static void sleep(long millis) throws InterruptedException {
    Thread.sleep(millis);
  }

  // -- windows --

  @Test
  public void theFocusedWindowIsTheScreenNamedAfterItsRoot() throws Exception {
    Recorded recorded = new Recorded();
    VBox root = new VBox();
    root.setId("orders");
    Stage stage = stage("Orders for Jane", root);
    FxInstrumentation fx = attached(recorded, false);
    onFx(() -> {
      fx.onFocus(stage, true);
      return null;
    });
    assertThat(recorded.calls, hasItem("screen VBox javafx.scene.layout.VBox javafx.scene.layout.VBox#orders"));
    assertTrue(fx.hasActiveWindow());
  }

  @Test
  public void withRecordTextTheTitleNamesTheScreen() throws Exception {
    Recorded recorded = new Recorded();
    Stage stage = stage("Orders", new Pane());
    FxInstrumentation fx = attached(recorded, true);
    onFx(() -> {
      fx.onFocus(stage, true);
      return null;
    });
    assertThat(recorded.calls, hasItem("screen Orders javafx.scene.layout.Pane javafx.scene.layout.Pane"));
  }

  @Test
  public void aBlankTitleOrNoSceneFallsBackToTheClass() throws Exception {
    Recorded recorded = new Recorded();
    FxInstrumentation fx = attached(recorded, true);
    onFx(() -> {
      Stage untitled = new Stage();
      untitled.setTitle("  ");
      fx.onFocus(untitled, true);
      fx.onFocus(new Stage(), true);
      return null;
    });
    // Real focus changes of other windows may report screens in between.
    assertThat(recorded.calls, containsInRelativeOrder("screen Stage javafx.stage.Stage javafx.stage.Stage",
        "screen Stage javafx.stage.Stage javafx.stage.Stage"));
  }

  @Test
  public void losingFocusIsTheBackgroundUnlessAnotherWindowGainsIt() throws Exception {
    Recorded recorded = new Recorded();
    Stage first = stage("a", new Pane());
    Stage second = stage("b", new Pane());
    FxInstrumentation fx = attached(recorded, false);
    onFx(() -> {
      fx.onFocus(first, true);
      fx.onFocus(first, false);
      fx.onFocus(second, true);
      return null;
    });
    sleep(DesktopInstrumentation.BACKGROUND_DELAY_MILLIS + 300);
    assertFalse("moving between windows", recorded.calls.contains("background"));

    onFx(() -> {
      fx.onFocus(first, false); // a window that was not focused
      fx.onFocus(second, false);
      return null;
    });
    sleep(DesktopInstrumentation.BACKGROUND_DELAY_MILLIS + 300);
    assertTrue(recorded.calls.contains("background"));
    assertFalse(fx.hasActiveWindow());

    onFx(() -> {
      fx.onFocus(second, true);
      return null;
    });
    assertEquals("foreground", recorded.calls.get(recorded.calls.size() - 2));
  }

  @Test
  public void noFocusedWindowAtStartIsTheBackground() throws Exception {
    Recorded recorded = new Recorded();
    instrumentation(recorded, true, false, false, () -> true).start();
    drainFx();
    sleep(DesktopInstrumentation.STARTUP_DELAY_MILLIS + 500);
    // A real window of an earlier test may still hold focus on some platforms; then nothing is reported.
    assertTrue(recorded.calls.isEmpty() || recorded.calls.contains("startedInBackground"));
  }

  @Test
  public void aWindowOpenedLaterIsWatchedAndAClosedOneForgotten() throws Exception {
    Recorded recorded = new Recorded();
    FxInstrumentation fx = attached(recorded, false);
    Button button = new Button("Go");
    Stage stage = stage("later", new VBox(button));
    onFx(() -> {
      Event.fireEvent(button, mouse(MouseEvent.MOUSE_CLICKED, button));
      fx.onFocus(stage, true);
      stage.hide();
      return null;
    });
    assertEquals(1, recorded.clicks.size());
    assertFalse("its window closed", fx.hasActiveWindow());
    onFx(() -> {
      Event.fireEvent(button, mouse(MouseEvent.MOUSE_CLICKED, button));
      return null;
    });
    assertEquals("no longer watched", 1, recorded.clicks.size());
  }

  // -- clicks --

  @Test
  public void aClickNamesTheControlAndItsPlace() throws Exception {
    Recorded recorded = new Recorded();
    Button save = new Button("Save");
    save.setId("save");
    VBox root = new VBox(save);
    root.setId("main");
    stage("s", root);
    attached(recorded, false);
    onFx(() -> {
      Node text = save.lookup(".text");
      Event.fireEvent(save, mouse(MouseEvent.MOUSE_PRESSED, text != null ? text : save));
      Event.fireEvent(save, mouse(MouseEvent.MOUSE_CLICKED, text != null ? text : save));
      return null;
    });
    Click click = recorded.clicks.get(0);
    assertEquals("Button", click.tag);
    assertEquals("javafx.scene.control.Button", click.classname);
    assertEquals("save", click.id);
    assertNull("no text without recordText", click.text);
    assertEquals("VBox#main/Button#save", click.xpath);
    assertEquals(105, click.x);
    assertEquals(205, click.y);
    assertTrue(click.startMillis <= click.endMillis);
  }

  @Test
  public void withRecordTextALabeledControlsTextIsKept() throws Exception {
    Recorded recorded = new Recorded();
    Label label = new Label("Total");
    Pane plain = new Pane();
    stage("s", new VBox(label, plain));
    attached(recorded, true);
    onFx(() -> {
      Event.fireEvent(label, mouse(MouseEvent.MOUSE_CLICKED, label));
      Event.fireEvent(plain, mouse(MouseEvent.MOUSE_CLICKED, plain));
      Event.fireEvent(plain, mouse(MouseEvent.MOUSE_CLICKED, null)); // nothing picked
      return null;
    });
    assertEquals(2, recorded.clicks.size());
    assertEquals("Total", recorded.clicks.get(0).text);
    assertEquals("Pane", recorded.clicks.get(1).tag);
    assertNull(recorded.clicks.get(1).text);
  }

  @Test
  public void aClickInAPopupCounts() throws Exception {
    Recorded recorded = new Recorded();
    Stage owner = stage("owner", new Pane());
    attached(recorded, false);
    Button item = new Button("Copy");
    onFx(() -> {
      Popup popup = new Popup();
      popup.getContent().add(item);
      popup.show(owner);
      windows.add(popup);
      return null;
    });
    onFx(() -> {
      Event.fireEvent(item, mouse(MouseEvent.MOUSE_CLICKED, item));
      return null;
    });
    assertEquals(1, recorded.clicks.size());
  }

  // -- attaching and stopping --

  @Test
  public void itWaitsForJavaFxToBeInUse() throws Exception {
    Recorded recorded = new Recorded();
    AtomicBoolean inUse = new AtomicBoolean(false);
    Button button = new Button("Go");
    stage("s", new VBox(button));
    instrumentation(recorded, false, true, false, inUse::get).start();
    sleep(DesktopInstrumentation.ATTACH_POLL_MILLIS + 300); // one poll finds JavaFX unused
    inUse.set(true);
    sleep(DesktopInstrumentation.ATTACH_POLL_MILLIS + 500);
    drainFx();
    onFx(() -> {
      Event.fireEvent(button, mouse(MouseEvent.MOUSE_CLICKED, button));
      return null;
    });
    assertEquals(1, recorded.clicks.size());
  }

  @Test
  public void stoppingDetaches() throws Exception {
    Recorded recorded = new Recorded();
    Button button = new Button("Go");
    stage("s", new VBox(button));
    FxInstrumentation fx = attached(recorded, false);
    fx.stop();
    fx.start(); // stopped: does nothing
    drainFx();
    onFx(() -> {
      Event.fireEvent(button, mouse(MouseEvent.MOUSE_CLICKED, button));
      return null;
    });
    assertThat(recorded.clicks, empty());
    assertFalse(fx.hasActiveWindow());
  }

  @Test
  public void stoppingBeforeAttachingNeverTouchesJavaFx() throws Exception {
    FxInstrumentation fx = instrumentation(new Recorded(), true, true, false, () -> false);
    fx.start();
    fx.stop();
    fx.attach(); // a late attach after stop does nothing
    onFx(() -> {
      fx.detach(); // nothing attached, nothing to remove
      return null;
    });
    fx.checkStartedInBackground(); // stopped: reports nothing
    assertFalse(fx.hasActiveWindow());
  }

  @Test
  public void nothingAskedForNothingStarted() {
    FxInstrumentation fx = instrumentation(new Recorded(), false, false, false, () -> {
      throw new AssertionError("must not check");
    });
    fx.start();
  }

  @Test
  public void theApplicationThreadTellsJavaFxIsInUse() {
    assertTrue(FxInstrumentation.fxThreadRunning());
  }

  @Test
  public void aSignalThatThrowsIsIgnored() throws Exception {
    ToolkitInstrumentation.Signals throwing = new Recorded() {
      @Override
      public void screenShown(String name, String screenClass, String screenId) {
        throw new IllegalStateException("boom");
      }

      @Override
      public void click(Click click) {
        throw new IllegalStateException("boom");
      }
    };
    Button button = new Button("Go");
    Stage stage = stage("s", new VBox(button));
    FxInstrumentation fx = attached(throwing, false);
    onFx(() -> {
      fx.onFocus(stage, true);
      Event.fireEvent(button, mouse(MouseEvent.MOUSE_CLICKED, button));
      return null;
    });
  }
  // -- edge states --

  @Test
  public void aWindowFocusedAtStartIsTheScreen() throws Exception {
    Recorded recorded = new Recorded();
    Stage stage = stage("s", new Pane());
    onFx(() -> {
      Popup popup = new Popup(); // focused too, but not a screen
      popup.show(stage);
      windows.add(popup);
      return null;
    });
    FxInstrumentation fx = instrumentation(recorded, true, true, false, () -> true);
    fx.isFocused = window -> true;
    fx.start();
    fx.start(); // already attaching: once is enough
    drainFx();
    assertThat(recorded.calls, hasItem("screen Pane javafx.scene.layout.Pane javafx.scene.layout.Pane"));
    assertTrue(fx.hasActiveWindow());
  }

  @Test
  public void aRealFocusChangeIsHeard() throws Exception {
    Recorded recorded = new Recorded();
    Stage stage = stage("s", new Pane());
    FxInstrumentation fx = attached(recorded, false);
    onFx(() -> {
      fx.watched.get(stage).focus.changed(null, false, true);
      return null;
    });
    assertTrue(fx.hasActiveWindow());
  }

  @Test
  public void aQueueThatRefusesWorkIsRetriedAndIgnoredAtStop() throws Exception {
    Recorded recorded = new Recorded();
    FxInstrumentation fx = instrumentation(recorded, true, true, false, () -> true);
    AtomicBoolean refuse = new AtomicBoolean(true);
    fx.runLater = task -> {
      if (refuse.get()) {
        throw new IllegalStateException("Toolkit not initialized");
      }
      Platform.runLater(task);
    };
    fx.start();
    refuse.set(false);
    sleep(DesktopInstrumentation.ATTACH_POLL_MILLIS + 500);
    drainFx();
    refuse.set(true);
    fx.stop(); // JavaFX has exited: nothing to detach
  }

  @Test
  public void watchingTwiceAndForgettingAStrangerAreHarmless() throws Exception {
    Recorded recorded = new Recorded();
    Button button = new Button("Go");
    Stage stage = stage("s", new VBox(button));
    FxInstrumentation fx = attached(recorded, false);
    onFx(() -> {
      fx.watch(stage);
      fx.unwatch(new Stage());
      Event.fireEvent(button, mouse(MouseEvent.MOUSE_CLICKED, button));
      return null;
    });
    assertEquals("one filter, not two", 1, recorded.clicks.size());
  }

  @Test
  public void windowsOnlyWatchesNoClicks() throws Exception {
    Recorded recorded = new Recorded();
    Button button = new Button("Go");
    stage("s", new VBox(button));
    instrumentation(recorded, true, false, false, () -> true).start();
    drainFx();
    onFx(() -> {
      Event.fireEvent(button, mouse(MouseEvent.MOUSE_CLICKED, button));
      return null;
    });
    assertThat(recorded.clicks, empty());
  }

  @Test
  public void theBackgroundChecksNeedTheForegroundAndNoFocus() throws Exception {
    Recorded recorded = new Recorded();
    Stage stage = stage("s", new Pane());
    FxInstrumentation fx = attached(recorded, false);
    onFx(() -> {
      fx.onFocus(stage, true);
      return null;
    });
    fx.checkBackground();
    fx.checkStartedInBackground();
    assertFalse("a window has focus", recorded.calls.contains("background"));
    onFx(() -> {
      fx.onFocus(stage, false);
      return null;
    });
    fx.checkBackground();
    fx.checkBackground();
    fx.checkStartedInBackground();
    assertEquals("once, and not again while in the background", 1,
        recorded.calls.stream().filter(c -> c.contains("ackground")).count());
    fx.stop();
    onFx(() -> {
      fx.onFocus(stage, true);
      return null;
    });
    fx.checkBackground();
  }

  @Test
  public void aPopupWithFocusIsStillAScreenWhenReported() throws Exception {
    Recorded recorded = new Recorded();
    Stage owner = stage("owner", new Pane());
    FxInstrumentation fx = attached(recorded, false);
    onFx(() -> {
      Popup popup = new Popup();
      popup.show(owner);
      windows.add(popup);
      fx.onFocus(popup, true);
      return null;
    });
    assertTrue(recorded.calls.stream().anyMatch(c -> c.startsWith("screen ")));
  }

  @Test
  public void otherMouseEventsAndAClickElsewhereAreTimedFromTheClick() throws Exception {
    Recorded recorded = new Recorded();
    Button a = new Button("A");
    Button b = new Button("B");
    stage("s", new VBox(a, b));
    FxInstrumentation fx = attached(recorded, false);
    onFx(() -> {
      fx.onMouse(mouse(MouseEvent.MOUSE_RELEASED, a));
      fx.onMouse(mouse(MouseEvent.MOUSE_PRESSED, a));
      fx.onMouse(mouse(MouseEvent.MOUSE_CLICKED, b));
      return null;
    });
    assertEquals(1, recorded.clicks.size());
    assertEquals(recorded.clicks.get(0).startMillis, recorded.clicks.get(0).endMillis);
  }

  @Test
  public void aDeepPathIsCut() {
    Pane leaf = new Pane();
    Pane parent = leaf;
    for (int i = 0; i < ToolkitSupport.MAX_PATH_DEPTH + 6; i++) {
      Pane next = new Pane(parent);
      parent = next;
    }
    assertEquals(ToolkitSupport.MAX_PATH_DEPTH, FxInstrumentation.path(leaf).split("/").length);
  }
}
