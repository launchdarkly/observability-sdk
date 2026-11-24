package com.launchdarkly.observability.internal;

import com.launchdarkly.logging.LDLogger;

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Link;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;
import org.eclipse.swt.widgets.Widget;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Supplier;

import static com.launchdarkly.observability.internal.SwtFixture.at;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** The SWT instrumentation: screens, the foreground and clicks. */
public class SwtInstrumentationTest {
  private Shell shell;
  private final Recorded recorded = new Recorded();
  private final List<SwtInstrumentation> instrumentations = new ArrayList<>();

  @Before
  public void setUp() {
    shell = SwtFixture.shell(300, 200);
  }

  @After
  public void tearDown() {
    instrumentations.forEach(SwtInstrumentation::stop);
    SwtFixture.pump(50);
    for (Shell s : SwtFixture.display().getShells()) {
      s.dispose();
    }
  }

  private SwtInstrumentation instrumentation(boolean recordText, Supplier<Display> display) {
    ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    SwtInstrumentation instrumentation = new SwtInstrumentation(recorded, true, true, recordText, LDLogger.none(),
        scheduler, display);
    instrumentations.add(instrumentation);
    return instrumentation;
  }

  private static Event event(int type, Widget widget, int x, int y) {
    Event event = new Event();
    event.type = type;
    event.widget = widget;
    event.x = x;
    event.y = y;
    return event;
  }

  @Test
  public void itAttachesOnceSwtsLoopRunsAndFollowsTheActiveShell() {
    SwtInstrumentation instrumentation = instrumentation(false, SwtSupport::display);
    instrumentation.start();
    SwtFixture.pumpUntil(() -> recorded.calls.contains("startedInBackground")
        || recorded.calls.stream().anyMatch(c -> c.startsWith("screen")));

    shell.setText("Jane's account");
    shell.notifyListeners(SWT.Activate, event(SWT.Activate, shell, 0, 0));
    assertTrue(instrumentation.hasActiveWindow());
    assertEquals("screen Shell org.eclipse.swt.widgets.Shell org.eclipse.swt.widgets.Shell", last());

    Text field = new Text(shell, SWT.NONE);
    instrumentation.onActivation(event(SWT.Activate, field, 0, 0));
    instrumentation.onActivation(event(SWT.Deactivate, field, 0, 0));
    assertTrue("a control's activation is not a shell's", instrumentation.hasActiveWindow());

    shell.notifyListeners(SWT.Deactivate, event(SWT.Deactivate, shell, 0, 0));
    assertFalse(instrumentation.hasActiveWindow());
    SwtFixture.pumpUntil(() -> "background".equals(last()));
    instrumentation.checkBackground();
    instrumentation.checkStartedInBackground();
    assertEquals("in the background once", "background", last());
    shell.notifyListeners(SWT.Activate, event(SWT.Activate, shell, 0, 0));
    assertTrue(recorded.calls.contains("foreground"));
  }

  @Test
  public void aShellIsTheScreenOfItsJFaceWindowAndItsTitleWithRecordText() {
    SwtInstrumentation instrumentation = instrumentation(true, () -> null);
    instrumentation.attach(SwtFixture.display());
    shell.setText("Settings");
    shell.setData(new AccountWindow());
    shell.setData("name", "settings");
    instrumentation.onActivation(event(SWT.Activate, shell, 0, 0));
    String type = AccountWindow.class.getName();
    assertEquals("screen Settings " + type + " " + type + "#settings", last());
    shell.setText(" ");
    instrumentation.onActivation(event(SWT.Activate, shell, 0, 0));
    assertEquals("screen AccountWindow " + type + " " + type + "#settings", last());
    shell.setData("a string is not a window");
    assertEquals(Shell.class, SwtSupport.screenClass(shell));
  }

  /** Like a JFace window, which keeps itself as its shell's data. */
  static final class AccountWindow {}

  @Test
  public void aClickIsAReleaseOnThePressedControl() {
    SwtInstrumentation instrumentation = instrumentation(true, () -> null);
    instrumentation.attach(SwtFixture.display());
    Composite panel = at(new Composite(shell, SWT.NONE), 10, 10, 200, 100);
    Button save = at(new Button(panel, SWT.PUSH), 5, 5, 80, 30);
    save.setText("Save");
    save.setData("name", "save");
    Label label = at(new Label(panel, SWT.NONE), 5, 50, 80, 20);
    label.setText("Total");

    save.notifyListeners(SWT.MouseDown, event(SWT.MouseDown, save, 10, 10));
    save.notifyListeners(SWT.MouseUp, event(SWT.MouseUp, save, 10, 10));
    Click click = recorded.clicks.get(0);
    assertEquals("Button", click.tag);
    assertEquals("org.eclipse.swt.widgets.Button", click.classname);
    assertEquals("save", click.id);
    assertEquals("Save", click.text);
    assertEquals("Shell/Composite/Button#save", click.xpath);
    org.eclipse.swt.graphics.Point screen = shell.getDisplay().map(save, null, 10, 10);
    assertEquals(screen.x, click.x);
    assertEquals(screen.y, click.y);

    instrumentation.onMouse(event(SWT.MouseDown, save, 10, 10));
    instrumentation.onMouse(event(SWT.MouseUp, label, 10, 10));
    instrumentation.onMouse(event(SWT.MouseDown, save, 10, 10));
    instrumentation.onMouse(event(SWT.MouseUp, save, 200, 10));
    instrumentation.onMouse(event(SWT.MouseDown, save, 10, 10));
    instrumentation.onMouse(event(SWT.MouseUp, save, -1, 10));
    instrumentation.onMouse(event(SWT.MouseDown, save, 10, 10));
    instrumentation.onMouse(event(SWT.MouseUp, save, 10, 100));
    instrumentation.onMouse(event(SWT.MouseDown, save, 10, 10));
    instrumentation.onMouse(event(SWT.MouseUp, save, 10, -1));
    Menu menu = new Menu(shell);
    instrumentation.onMouse(event(SWT.MouseUp, menu, 1, 1));
    assertEquals("released elsewhere, or on a menu, is no click", 1, recorded.clicks.size());

    instrumentation.onMouse(event(SWT.MouseDown, label, 1, 1));
    instrumentation.onMouse(event(SWT.MouseUp, label, 1, 1));
    assertEquals("Total", recorded.clicks.get(1).text);
    assertNull(recorded.clicks.get(1).id);

    SwtInstrumentation quiet = instrumentation(false, () -> null);
    quiet.onMouse(event(SWT.MouseDown, save, 1, 1));
    quiet.onMouse(event(SWT.MouseUp, save, 1, 1));
    assertNull("no text without recordText", recorded.clicks.get(2).text);

    save.dispose();
    instrumentation.onMouse(event(SWT.MouseUp, save, 1, 1));
    assertEquals("a disposed control is ignored", 3, recorded.clicks.size());
  }

  @Test
  public void aPathHasAtMostItsDepth() {
    org.eclipse.swt.widgets.Composite parent = shell;
    for (int i = 0; i < ToolkitSupport.MAX_PATH_DEPTH + 5; i++) {
      parent = new Composite(parent, SWT.NONE);
    }
    assertEquals(ToolkitSupport.MAX_PATH_DEPTH, SwtSupport.path(parent).split("/").length);
  }

  @Test
  public void onlyButtonLabelAndLinkTextIsRecorded() {
    Link link = new Link(shell, SWT.NONE);
    link.setText("See <a href=\"https://example.com\">terms</a>");
    assertEquals("See terms", SwtSupport.text(link));
    org.eclipse.swt.custom.CLabel cLabel = new org.eclipse.swt.custom.CLabel(shell, SWT.NONE);
    cLabel.setText("Hi");
    assertEquals("Hi", SwtSupport.text(cLabel));
    Text text = new Text(shell, SWT.NONE);
    text.setText("secret");
    assertNull(SwtSupport.text(text));
    shell.setData("name", "");
    assertNull("an empty name is none", SwtSupport.name(shell));
    shell.setData("name", 42);
    assertNull(SwtSupport.name(shell));
  }

  @Test
  public void attachedWithAShellActiveThatIsTheScreen() {
    SwtInstrumentation instrumentation = instrumentation(false, () -> null);
    instrumentation.activeShell = d -> shell;
    instrumentation.attach(SwtFixture.display());
    assertTrue(instrumentation.hasActiveWindow());
    assertEquals("screen Shell org.eclipse.swt.widgets.Shell org.eclipse.swt.widgets.Shell", last());
  }

  @Test
  public void startedInTheBackgroundWithoutAnActiveShell() {
    shell.dispose(); // no shell to become active while it waits
    SwtFixture.pump(100);
    SwtInstrumentation instrumentation = instrumentation(false, () -> null);
    instrumentation.activeShell = d -> null;
    instrumentation.attach(SwtFixture.display());
    assertFalse(instrumentation.hasActiveWindow());
    SwtFixture.pumpUntil(() -> recorded.calls.contains("startedInBackground"));
    instrumentation.checkStartedInBackground();
    assertEquals("once", 1, recorded.calls.stream().filter("startedInBackground"::equals).count());
  }

  @Test
  public void onlyWhatIsAskedFor() {
    SwtInstrumentation screens = new SwtInstrumentation(recorded, true, false, false, LDLogger.none(),
        Executors.newSingleThreadScheduledExecutor(), () -> null);
    SwtInstrumentation clicks = new SwtInstrumentation(recorded, false, true, false, LDLogger.none(),
        Executors.newSingleThreadScheduledExecutor(), () -> null);
    instrumentations.add(screens);
    instrumentations.add(clicks);
    clicks.start();
    screens.attach(SwtFixture.display());
    clicks.attach(SwtFixture.display());
    Button button = at(new Button(shell, SWT.PUSH), 0, 0, 50, 20);
    button.notifyListeners(SWT.MouseDown, event(SWT.MouseDown, button, 1, 1));
    button.notifyListeners(SWT.MouseUp, event(SWT.MouseUp, button, 1, 1));
    assertEquals("one click, from the instrumentation watching clicks", 1, recorded.clicks.size());
    recorded.calls.clear();
    shell.notifyListeners(SWT.Activate, event(SWT.Activate, shell, 0, 0));
    assertEquals("one screen, from the one watching windows", 1, recorded.calls.size());
  }

  @Test
  public void focusMovingBetweenShellsStaysInTheForeground() {
    SwtInstrumentation instrumentation = instrumentation(false, () -> null);
    instrumentation.attach(SwtFixture.display());
    Shell other = SwtFixture.shell(50, 50);
    instrumentation.onActivation(event(SWT.Activate, shell, 0, 0));
    instrumentation.onActivation(event(SWT.Deactivate, other, 0, 0));
    assertTrue("another shell's loss leaves this one active", instrumentation.hasActiveWindow());
    instrumentation.onActivation(event(SWT.Deactivate, shell, 0, 0));
    instrumentation.onActivation(event(SWT.Activate, other, 0, 0));
    SwtFixture.pump(DesktopInstrumentation.BACKGROUND_DELAY_MILLIS + 200);
    assertFalse(recorded.calls.contains("background"));
    instrumentation.checkBackground();
    assertFalse("a check with a shell active", recorded.calls.contains("background"));
  }

  @Test
  public void aFailingSignalIsIgnored() {
    ToolkitInstrumentation.Signals failing = new ToolkitInstrumentation.Signals() {
      @Override
      public void foreground() {}

      @Override
      public void background() {}

      @Override
      public void startedInBackground() {}

      @Override
      public void screenShown(String name, String screenClass, String screenId) {
        throw new IllegalStateException("broken");
      }

      @Override
      public void click(Click click) {
        throw new IllegalStateException("broken");
      }
    };
    SwtInstrumentation instrumentation = new SwtInstrumentation(failing, true, true, false, LDLogger.none(),
        Executors.newSingleThreadScheduledExecutor(), () -> null);
    instrumentations.add(instrumentation);
    instrumentation.onActivation(event(SWT.Activate, shell, 0, 0));
    instrumentation.onMouse(event(SWT.MouseDown, shell, 1, 1));
    instrumentation.onMouse(event(SWT.MouseUp, shell, 1, 1));
  }

  @Test
  public void stoppedItStopsListening() throws Exception {
    SwtInstrumentation instrumentation = instrumentation(false, SwtFixture::display);
    assertTrue(SwtFixture.offThread(instrumentation::tryAttach));
    SwtFixture.pump(100);
    instrumentation.stop();
    SwtFixture.pump(100);
    recorded.calls.clear(); // the shell active when it attached
    shell.notifyListeners(SWT.Activate, event(SWT.Activate, shell, 0, 0));
    assertFalse(instrumentation.hasActiveWindow());
    assertTrue("stopped, it is done", instrumentation.tryAttach());
    instrumentation.attach(SwtFixture.display());
    assertFalse(instrumentation.hasActiveWindow());
    instrumentation.checkBackground();
    instrumentation.checkStartedInBackground();
    assertThat(recorded.calls, empty());

    SwtInstrumentation never = instrumentation(false, () -> null);
    assertFalse("no SWT yet", never.tryAttach());
    SwtInstrumentation idle = new SwtInstrumentation(recorded, false, false, false, LDLogger.none(),
        Executors.newSingleThreadScheduledExecutor(), () -> {
          throw new AssertionError("never looked for");
        });
    idle.start();
    idle.stop();
  }

  @Test
  public void theProductionInstrumentationIsLoadedByName() {
    ToolkitInstrumentation swt = FxSupport.create("SWT", FxSupport.SWT_PROBE_CLASS, FxSupport.SWT_INSTRUMENTATION_CLASS,
        recorded, true, true, false, LDLogger.none());
    assertNotNull(swt);
    swt.start(); // its own thread looks for SWT
    SwtFixture.pump(200);
    swt.stop();
    List<ToolkitInstrumentation> toolkits = new ArrayList<>();
    FxSupport.addTo(toolkits, recorded, true, true, false, LDLogger.none());
    assertTrue(toolkits.stream().anyMatch(t -> t instanceof SwtInstrumentation));
    toolkits.forEach(ToolkitInstrumentation::stop);
    assertThat(ToolkitSupport.swtThread(), org.hamcrest.Matchers.nullValue()); // this thread is not in the loop now
  }

  private String last() {
    return recorded.calls.isEmpty() ? null : recorded.calls.get(recorded.calls.size() - 1);
  }

  private static final class Recorded implements ToolkitInstrumentation.Signals {
    final List<String> calls = new ArrayList<>();
    final List<Click> clicks = new ArrayList<>();

    @Override
    public synchronized void foreground() {
      calls.add("foreground");
    }

    @Override
    public synchronized void background() {
      calls.add("background");
    }

    @Override
    public synchronized void startedInBackground() {
      calls.add("startedInBackground");
    }

    @Override
    public synchronized void screenShown(String name, String screenClass, String screenId) {
      calls.add("screen " + name + " " + screenClass + " " + screenId);
    }

    @Override
    public synchronized void click(Click click) {
      clicks.add(click);
    }
  }
}
