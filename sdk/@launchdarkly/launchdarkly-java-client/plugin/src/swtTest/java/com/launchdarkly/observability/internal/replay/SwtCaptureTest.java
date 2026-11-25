package com.launchdarkly.observability.internal.replay;

import com.launchdarkly.logging.LDLogger;
import com.launchdarkly.observability.internal.SwtFixture;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.PaletteData;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static com.launchdarkly.observability.internal.SwtFixture.at;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/** Replay's capture of an SWT shell. */
public class SwtCaptureTest {
  private static final boolean COCOA = "cocoa".equals(SWT.getPlatform());
  private Shell shell;
  private final List<String> pointer = new ArrayList<>();

  @Before
  public void setUp() {
    shell = SwtFixture.shell(300, 200);
  }

  @After
  public void tearDown() {
    for (Shell s : SwtFixture.display().getShells()) {
      s.dispose();
    }
  }

  private SwtCapture capture(Shell active) {
    SwtCapture capture = new SwtCapture(1, new SwtMasks(MaskRules.defaults()), LDLogger.none(), SwtFixture::display);
    capture.activeShell = d -> active;
    return capture;
  }

  private Composite block(Composite parent, int x, int y, int color) {
    Composite block = at(new Composite(parent, SWT.NONE), x, y, 60, 40);
    block.setBackground(new Color(parent.getDisplay(), (color >> 16) & 0xff, (color >> 8) & 0xff, color & 0xff));
    return block;
  }

  @Test
  public void theFrameIsTheShellMasked() {
    block(shell, 20, 20, 0xff0000);
    at(new Text(shell, SWT.PASSWORD), 150, 20, 100, 25).setText("hunter2");
    SwtFixture.pump(300);
    for (boolean fromWindow : new boolean[] {true, false}) {
      SwtCapture capture = capture(shell);
      capture.copyFromWindow = fromWindow;
      SwtCapture.Painted painted = capture.paint(SwtFixture.display());
      assertEquals(300, painted.width);
      assertEquals(200, painted.height);
      assertThat(painted.masks, contains(SwtMaskTest.covered(150, 20, 100, 25)));
      assertEquals("masked", CaptureLoop.MASK_COLOR.getRGB(), painted.pixels[30 * 300 + 200]);
      Point origin = shell.getDisplay().map(shell, null, 0, 0);
      assertEquals(origin.x, painted.originX);
      assertEquals(origin.y, painted.originY);
      // SWT prints controls out of place on macOS, which is why it copies from the window there.
      if (fromWindow == COCOA) {
        int red = painted.pixels[40 * 300 + 50];
        // The screen's color profile can shift a channel a little.
        assertTrue("the red block: " + Integer.toHexString(red), ((red >> 16) & 0xff) > 0xf0
            && ((red >> 8) & 0xff) < 0x20 && (red & 0xff) < 0x20);
      }
    }
  }

  @Test
  public void aDialogIsDrawnOverItsShell() {
    Shell dialog = new Shell(shell, SWT.DIALOG_TRIM);
    dialog.setSize(dialog.computeTrim(0, 0, 100, 80).width, dialog.computeTrim(0, 0, 100, 80).height);
    Point at = shell.getDisplay().map(shell, null, 100, 60);
    dialog.setLocation(at.x, at.y);
    dialog.open();
    Shell hidden = new Shell(shell, SWT.DIALOG_TRIM);
    hidden.setSize(50, 50);
    at(new Text(dialog, SWT.NONE), 10, 10, 60, 20);
    SwtFixture.pump(300);
    SwtCapture capture = capture(dialog);
    capture.copyFromWindow = COCOA;
    SwtCapture.Painted painted = capture.paint(SwtFixture.display());
    Point inside = shell.getDisplay().map(dialog, shell, 0, 0);
    assertEquals("the frame is the dialog's shell", 300, painted.width);
    assertThat("the dialog's field, where the dialog is", painted.masks,
        contains(SwtMaskTest.covered(inside.x + 10, inside.y + 10, 60, 20)));
  }

  @Test
  public void aDialogOverAHiddenShellIsItsOwnFrame() {
    Shell dialog = new Shell(shell, SWT.DIALOG_TRIM);
    dialog.setSize(dialog.computeTrim(0, 0, 100, 80).width, dialog.computeTrim(0, 0, 100, 80).height);
    dialog.open();
    shell.setVisible(false);
    SwtFixture.pump(200);
    SwtCapture.Painted painted = capture(dialog).paint(SwtFixture.display());
    assertEquals(dialog.getClientArea().width, painted.width);
  }

  @Test
  public void noActiveShellNoFrame() {
    assertNull(capture(null).paint(SwtFixture.display()));
    shell.setVisible(false);
    assertNull("not visible", capture(shell).paint(SwtFixture.display()));
    shell.setVisible(true);
    shell.setSize(0, 0);
    SwtFixture.pump(100);
    if (shell.getClientArea().width == 0 || shell.getClientArea().height == 0) {
      assertNull("no room", capture(shell).paint(SwtFixture.display()));
    }
  }

  @Test
  public void captureRunsOnSwtsThreadAndWaitsForIt() throws Exception {
    SwtCapture capture = capture(shell);
    CaptureSource.RawFrame frame = SwtFixture.offThread(() -> capture.capture(5000));
    assertNotNull(frame);
    assertEquals(300, frame.image.getWidth());
    assertEquals(1, frame.scale, 0);

    SwtCapture half = new SwtCapture(0.5, new SwtMasks(MaskRules.defaults()), LDLogger.none(), SwtFixture::display);
    half.activeShell = d -> shell;
    assertEquals(150, SwtFixture.offThread(() -> half.capture(5000)).image.getWidth());

    // Nothing dispatches here, so the capture gives up.
    assertNull(capture.capture(50));
    SwtFixture.pump(100);

    capture.activeShell = d -> null;
    assertNull(SwtFixture.offThread(() -> capture.capture(5000)));
    capture.activeShell = d -> {
      throw new IllegalStateException("broken");
    };
    assertNull("a failure skips the frame", SwtFixture.offThread(() -> capture.capture(5000)));

    SwtCapture none = new SwtCapture(1, new SwtMasks(MaskRules.defaults()), LDLogger.none(), () -> null);
    assertNull("no SWT running", none.capture(50));
    none.stop();

    Thread.currentThread().interrupt();
    assertNull(capture(shell).capture(5000));
    assertTrue(Thread.interrupted());
  }

  @Test
  public void theMouseIsReportedOnTheScreen() {
    SwtCapture capture = capture(shell);
    capture.pointer = (action, x, y, timestamp) -> pointer.add(action + " " + (int) x + "," + (int) y);
    capture.paint(SwtFixture.display());
    capture.paint(SwtFixture.display()); // listening once
    Point origin = shell.getDisplay().map(shell, null, 0, 0);
    shell.notifyListeners(SWT.MouseMove, event(SWT.MouseMove, 5, 6));
    Event down = event(SWT.MouseDown, 7, 8);
    capture.mouseEvent(down);
    Event up = event(SWT.MouseUp, 7, 8);
    up.widget = null;
    capture.mouseEvent(up);
    capture.mouseEvent(event(SWT.KeyDown, 1, 1));
    Point cursor = shell.getDisplay().getCursorLocation();
    assertThat(pointer, contains("MOVE " + (origin.x + 5) + "," + (origin.y + 6),
        "DOWN " + (origin.x + 7) + "," + (origin.y + 8), "UP " + cursor.x + "," + cursor.y));

    capture.stop();
    SwtFixture.pump(100);
    pointer.clear();
    shell.notifyListeners(SWT.MouseMove, event(SWT.MouseMove, 5, 6));
    assertThat("stopped", pointer, org.hamcrest.Matchers.empty());
    capture.pointer = null;
    capture.mouseEvent(event(SWT.MouseMove, 1, 1));
  }

  private Event event(int type, int x, int y) {
    Event event = new Event();
    event.type = type;
    event.widget = shell;
    event.display = shell.getDisplay();
    event.x = x;
    event.y = y;
    return event;
  }

  @Test
  public void imageDataIsReadThroughItsPalette() {
    ImageData indexed = new ImageData(2, 1, 8, new PaletteData(new RGB[] {new RGB(1, 2, 3), new RGB(250, 0, 5)}));
    indexed.setPixel(1, 0, 1);
    int[] into = new int[4];
    SwtCapture.read(indexed, into, 2, 2, 0, 1);
    assertEquals(0x010203, into[2]);
    assertEquals(0xfa0005, into[3]);
    int[] cut = new int[1];
    SwtCapture.read(indexed, cut, 1, 1, -1, -1);
    SwtCapture.read(indexed, cut, 1, 1, 0, 5);
    SwtCapture.read(indexed, cut, 1, 1, 0, 0);
    assertEquals("cut to the frame", 0x010203, cut[0]);
    SwtCapture.read(indexed, cut, 1, 1, -1, 0);
    assertEquals("from the left", 0xfa0005, cut[0]);
    // A 16-bit palette's channels shift left into eight bits.
    PaletteData rgb565 = new PaletteData(0xf800, 0x07e0, 0x001f);
    assertEquals(0xf8fcf8, SwtCapture.rgb(rgb565, 0xffff));
  }

  @Test
  public void theProductionCaptureFindsTheRunningDisplay() throws Exception {
    CaptureSource source = SwtCapture.create(1, MaskRules.defaults(), LDLogger.none(), null);
    assertTrue(source instanceof SwtCapture);
    // The display is found from the threads while this one runs SWT's loop. One look can catch this
    // thread outside the loop, between events, so the plugin looks again, as this does.
    Display found = SwtFixture.offThread(() -> {
      for (int i = 0; i < 100; i++) {
        Display d = com.launchdarkly.observability.internal.SwtSupport.display();
        if (d != null) {
          return d;
        }
        Thread.sleep(20);
      }
      return null;
    });
    assertSame(SwtFixture.display(), found);
    List<CaptureSource> sources = ReplayService.sources(1, MaskRules.defaults(), LDLogger.none(), null);
    assertTrue(sources.stream().anyMatch(s -> s instanceof SwtCapture));
  }
}
