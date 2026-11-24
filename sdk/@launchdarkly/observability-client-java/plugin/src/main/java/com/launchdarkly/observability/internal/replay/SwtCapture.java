package com.launchdarkly.observability.internal.replay;

import com.launchdarkly.logging.LDLogger;
import com.launchdarkly.observability.internal.SwtSupport;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.PaletteData;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Shell;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Captures the active SWT shell. One task on SWT's thread copies the shell, then the shells it owns,
 * such as dialogs, each at its place over it, and masks each as soon as it is copied in, before the
 * next goes over it. Nothing draws in between, so the masks are where the pixels are. The frame
 * handed on is already masked.
 * <p>
 * A shell is copied two ways. On macOS, from what the window last drew, since SWT's printing draws
 * there with its controls misplaced; that copy is the window's own, whatever is over it. Elsewhere,
 * by printing the shell, which draws it as it is, whatever is over it on the screen.
 * <p>
 * Once a frame is taken, the mouse anywhere in the application is reported to the pointer sink, if
 * there is one. Loaded only when SWT is present, through {@link #create}.
 */
final class SwtCapture implements CaptureSource {
  private final double scale;
  private final SwtMasks masks;
  private final LDLogger logger;
  private final Supplier<Display> findDisplay;
  // Replaced in tests: the active shell, which a test cannot make active, and how a shell is copied.
  Function<Display, Shell> activeShell = Display::getActiveShell;
  boolean copyFromWindow = "cocoa".equals(SWT.getPlatform());
  /** Where the mouse is reported, or null. Set before the first capture. */
  volatile PointerSink pointer;

  private volatile Display display;
  // Touched only on SWT's thread.
  private final Listener onMouse = this::mouseEvent;
  private boolean listening;

  /** The production capture, called by name, so this class loads only with SWT. */
  static CaptureSource create(double scale, MaskRules rules, LDLogger logger, PointerSink pointer) {
    SwtCapture capture = new SwtCapture(scale, new SwtMasks(rules), logger, SwtSupport::display);
    capture.pointer = pointer;
    return capture;
  }

  SwtCapture(double scale, SwtMasks masks, LDLogger logger, Supplier<Display> findDisplay) {
    this.scale = scale;
    this.masks = masks;
    this.logger = logger;
    this.findDisplay = findDisplay;
  }

  /** A copy taken on SWT's thread: the pixels at scale 1, already masked, and where the shell is. */
  static final class Painted {
    final int[] pixels;
    final int width;
    final int height;
    final List<Rectangle> masks;
    final int originX;
    final int originY;

    Painted(int[] pixels, int width, int height, List<Rectangle> masks, int originX, int originY) {
      this.pixels = pixels;
      this.width = width;
      this.height = height;
      this.masks = masks;
      this.originX = originX;
      this.originY = originY;
    }
  }

  /** The display, found once SWT runs, and again after it is disposed. */
  private Display display() {
    Display d = display;
    if (d == null || d.isDisposed()) {
      d = findDisplay.get();
      display = d;
    }
    return d;
  }

  @Override
  public RawFrame capture(long timeoutMillis) {
    Display d = display();
    if (d == null) {
      return null; // SWT is not running; nothing of it is touched until it is
    }
    FutureTask<Painted> task = new FutureTask<>(() -> paint(d));
    try {
      d.asyncExec(task);
    } catch (RuntimeException e) {
      return null; // disposed since
    }
    Painted painted;
    try {
      painted = task.get(timeoutMillis, TimeUnit.MILLISECONDS);
    } catch (TimeoutException e) {
      task.cancel(false); // the thread is busy: skip this frame rather than add to its work
      return null;
    } catch (ExecutionException e) {
      logger.debug("Session replay could not copy an SWT shell: {}", e.getCause().toString());
      return null;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return null;
    }
    if (painted == null) {
      return null;
    }
    BufferedImage image = Pixels.image(painted.pixels, painted.width, painted.height, scale);
    return new RawFrame(image, Collections.<Rectangle>emptyList(), System.currentTimeMillis(), painted.originX,
        painted.originY, image.getWidth() / (double) painted.width);
  }

  /** The copy, or null if no shell is active. On SWT's thread. */
  Painted paint(Display d) {
    if (pointer != null && !listening) {
      d.addFilter(SWT.MouseMove, onMouse);
      d.addFilter(SWT.MouseDown, onMouse);
      d.addFilter(SWT.MouseUp, onMouse);
      listening = true;
      display = d; // where stop takes them off
    }
    Shell active = activeShell.apply(d);
    if (active == null || !active.isVisible()) {
      return null;
    }
    // A dialog is shown over the shell it belongs to, so the frame is that shell, with the dialog on it.
    Shell base = active;
    while (base.getParent() instanceof Shell && base.getParent().isVisible()) {
      base = (Shell) base.getParent();
    }
    org.eclipse.swt.graphics.Rectangle area = base.getClientArea();
    int w = area.width;
    int h = area.height;
    if (Math.min(w, h) <= 0) {
      return null;
    }
    int[] pixels = new int[w * h];
    List<Rectangle> applied = new ArrayList<>();
    draw(base, pixels, w, h, 0, 0, applied);
    List<Shell> over = new ArrayList<>();
    for (Shell owned : base.getShells()) { // every shell under it, however deep
      if (owned.isVisible()) {
        over.add(owned);
      }
    }
    // The active shell goes on top of the others the base owns.
    if (over.remove(active)) {
      over.add(active);
    }
    for (Shell shell : over) {
      Point at = d.map(shell, base, 0, 0);
      draw(shell, pixels, w, h, at.x, at.y, applied);
    }
    Point origin = d.map(base, null, 0, 0);
    return new Painted(pixels, w, h, applied, origin.x, origin.y);
  }

  /** Copies one shell's client area into the frame at its place, then masks it. */
  private void draw(Shell shell, int[] pixels, int w, int h, int x, int y, List<Rectangle> applied) {
    org.eclipse.swt.graphics.Rectangle area = shell.getClientArea();
    if (Math.min(area.width, area.height) <= 0) {
      return;
    }
    Image image = new Image(shell.getDisplay(), area.width, area.height);
    try {
      GC gc = new GC(copyFromWindow ? shell : image);
      try {
        if (copyFromWindow) {
          gc.copyArea(image, 0, 0);
        } else {
          shell.print(gc);
        }
      } finally {
        gc.dispose();
      }
      read(image.getImageData(), pixels, w, h, x, y);
    } finally {
      image.dispose();
    }
    List<Rectangle> layer = new ArrayList<>();
    masks.collect(shell, x, y, layer);
    Pixels.mask(pixels, w, h, layer);
    applied.addAll(layer);
  }

  /** Copies image data into the frame's pixels at its place, cut to the frame. */
  static void read(ImageData data, int[] into, int width, int height, int x, int y) {
    int[] row = new int[data.width];
    for (int sy = 0; sy < data.height; sy++) {
      int ty = y + sy;
      if (ty < 0 || ty >= height) {
        continue;
      }
      data.getPixels(0, sy, data.width, row, 0);
      for (int sx = 0; sx < data.width; sx++) {
        int tx = x + sx;
        if (tx >= 0 && tx < width) {
          into[ty * width + tx] = rgb(data.palette, row[sx]);
        }
      }
    }
  }

  /** A pixel of image data as 0xRRGGBB. */
  static int rgb(PaletteData palette, int pixel) {
    if (palette.isDirect) {
      return channel(pixel, palette.redMask, palette.redShift) << 16
          | channel(pixel, palette.greenMask, palette.greenShift) << 8
          | channel(pixel, palette.blueMask, palette.blueShift);
    }
    RGB color = palette.colors[pixel];
    return color.red << 16 | color.green << 8 | color.blue;
  }

  /** One 8-bit channel, as SWT shifts it: a negative shift is to the right. */
  private static int channel(int pixel, int mask, int shift) {
    int value = pixel & mask;
    return (shift < 0 ? value >>> -shift : value << shift) & 0xff;
  }

  /** Reports a press, release or move to the pointer sink. On SWT's thread. */
  void mouseEvent(Event event) {
    PointerSink sink = pointer;
    ReplayItem.Pointer.Action action = action(event.type);
    if (sink == null || action == null) {
      return;
    }
    Point screen = event.widget instanceof Control
        ? event.display.map((Control) event.widget, null, event.x, event.y) : event.display.getCursorLocation();
    sink.pointer(action, screen.x, screen.y, System.currentTimeMillis());
  }

  static ReplayItem.Pointer.Action action(int type) {
    switch (type) {
      case SWT.MouseDown:
        return ReplayItem.Pointer.Action.DOWN;
      case SWT.MouseUp:
        return ReplayItem.Pointer.Action.UP;
      case SWT.MouseMove:
        return ReplayItem.Pointer.Action.MOVE;
      default:
        return null;
    }
  }

  /** Takes the mouse listeners off the display. */
  @Override
  public void stop() {
    Display d = display;
    if (d == null) {
      return;
    }
    try {
      d.asyncExec(() -> { // run only while the display is not disposed
        d.removeFilter(SWT.MouseMove, onMouse);
        d.removeFilter(SWT.MouseDown, onMouse);
        d.removeFilter(SWT.MouseUp, onMouse);
        listening = false;
      });
    } catch (RuntimeException e) {
      // SWT has exited, and its listeners with it.
    }
  }
}
