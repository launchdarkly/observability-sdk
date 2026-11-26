package com.launchdarkly.observability.internal.replay;

import com.launchdarkly.logging.LDLogger;
import com.launchdarkly.observability.internal.ComposeSupport;
import com.launchdarkly.observability.internal.ToolkitSupport;

import java.awt.AWTEvent;
import java.awt.Component;
import java.awt.Container;
import java.awt.EventQueue;
import java.awt.Graphics2D;
import java.awt.KeyboardFocusManager;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.AWTEventListener;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import javax.swing.RootPaneContainer;

/**
 * Captures the active Swing or AWT window. Everything happens in one task on the event dispatch
 * thread: the window, its dialogs and heavyweight popups are painted offscreen into one image, and
 * each is masked as soon as it is painted, before the next is painted over it, so a dialog over a
 * masked component stays visible. Nothing repaints in between, so the masks are where the painted
 * pixels are. The frame handed on is already masked.
 * <p>
 * The frame is the window's content, without the title bar the operating system draws, at the
 * window's size in logical pixels, times the scale. On a HiDPI screen that is fewer pixels than the
 * screen shows, which keeps capture cheap.
 * <p>
 * Compose for Desktop draws on a GPU canvas, which painting offscreen leaves blank, so each Compose
 * layer is filled with the last frame skiko drew on it, in the same task.
 * <p>
 * Once AWT is in use, the mouse anywhere in the application's windows is reported to the pointer
 * sink, if there is one.
 */
final class SwingCapture implements CaptureSource {
  /** Finds what to mask in a window painted at an offset, adding rectangles in frame pixels. */
  interface Masks {
    void collect(Component content, int offsetX, int offsetY, double scale, List<Rectangle> out);
  }

  private final double scale;
  private final Masks masks;
  private final LDLogger logger;
  private final BooleanSupplier awtInUse;
  private final Supplier<Window> activeWindow;
  /** Where the mouse is reported, or null. Set before the first capture. */
  volatile PointerSink pointer;
  private volatile AWTEventListener mouse;

  SwingCapture(double scale, Masks masks, LDLogger logger, BooleanSupplier awtInUse) {
    this(scale, masks, logger, awtInUse,
        () -> KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow());
  }

  SwingCapture(double scale, Masks masks, LDLogger logger, BooleanSupplier awtInUse, Supplier<Window> activeWindow) {
    this.scale = scale;
    this.masks = masks;
    this.logger = logger;
    this.awtInUse = awtInUse;
    this.activeWindow = activeWindow;
  }

  @Override
  public RawFrame capture(long timeoutMillis) {
    if (!awtInUse.getAsBoolean()) {
      return null; // asking AWT for its windows would start it
    }
    if (pointer != null && mouse == null) {
      AWTEventListener listener = this::mouseEvent;
      Toolkit.getDefaultToolkit().addAWTEventListener(listener,
          AWTEvent.MOUSE_EVENT_MASK | AWTEvent.MOUSE_MOTION_EVENT_MASK);
      mouse = listener;
    }
    FutureTask<RawFrame> task = new FutureTask<>(this::paint);
    EventQueue.invokeLater(task);
    try {
      return task.get(timeoutMillis, TimeUnit.MILLISECONDS);
    } catch (TimeoutException e) {
      task.cancel(false); // the thread is busy: skip this frame rather than add to its work
      return null;
    } catch (ExecutionException e) {
      logger.debug("Session replay could not paint a Swing window: {}", e.getCause().toString());
      return null;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return null;
    }
  }

  @Override
  public void stop() {
    AWTEventListener listener = mouse;
    if (listener != null) {
      Toolkit.getDefaultToolkit().removeAWTEventListener(listener);
      mouse = null;
    }
  }

  /** Reports a press, release or move to the pointer sink. On the event dispatch thread. */
  void mouseEvent(AWTEvent event) {
    MouseEvent e = (MouseEvent) event; // the listener's mask lets only mouse events through
    ReplayItem.Pointer.Action action = action(e.getID());
    if (action != null) {
      pointer.pointer(action, e.getXOnScreen(), e.getYOnScreen(), e.getWhen());
    }
  }

  static ReplayItem.Pointer.Action action(int id) {
    switch (id) {
      case MouseEvent.MOUSE_PRESSED:
        return ReplayItem.Pointer.Action.DOWN;
      case MouseEvent.MOUSE_RELEASED:
        return ReplayItem.Pointer.Action.UP;
      case MouseEvent.MOUSE_MOVED:
      case MouseEvent.MOUSE_DRAGGED:
        return ReplayItem.Pointer.Action.MOVE;
      default:
        return null;
    }
  }

  /** On the event dispatch thread. */
  RawFrame paint() {
    Window active = activeWindow.get();
    if (active == null || !active.isShowing()) {
      return null;
    }
    // A dialog is shown over the window it belongs to, so the frame is that window, with the dialog on it.
    Window base = active;
    while (base.getOwner() != null && base.getOwner().isShowing()) {
      base = base.getOwner();
    }
    Component content = content(base);
    int width = (int) Math.round(content.getWidth() * scale);
    int height = (int) Math.round(content.getHeight() * scale);
    if (Math.min(width, height) <= 0) {
      return null;
    }
    Point origin = content.getLocationOnScreen();
    BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
    Graphics2D g = image.createGraphics();
    try {
      g.setColor(content.getBackground()); // none leaves the default
      g.fillRect(0, 0, width, height);
      g.scale(scale, scale);
      draw(image, g, content, 0, 0);
      List<Window> owned = new ArrayList<>();
      ownedWindows(base, owned);
      // The active window goes on top of the others it shares an owner with.
      if (owned.remove(active)) {
        owned.add(active);
      }
      for (Window window : owned) {
        Component windowContent = content(window);
        Point at = windowContent.getLocationOnScreen();
        draw(image, g, windowContent, at.x - origin.x, at.y - origin.y);
      }
    } finally {
      g.dispose();
    }
    return new RawFrame(image, Collections.<Rectangle>emptyList(), System.currentTimeMillis(), origin.x, origin.y,
        scale);
  }

  /** Paints one window's content at its place, then masks it. {@code g} is scaled; the masks are in frame pixels. */
  private void draw(BufferedImage image, Graphics2D g, Component content, int x, int y) {
    Graphics2D child = (Graphics2D) g.create(x, y, content.getWidth(), content.getHeight());
    try {
      content.printAll(child);
      drawCompose(child, content);
    } finally {
      child.dispose();
    }
    List<Rectangle> layer = new ArrayList<>();
    masks.collect(content, x, y, scale, layer);
    CaptureLoop.mask(image, layer);
  }

  /**
   * Fills each Compose layer in a component with skiko's last frame of it. {@code g} is the
   * component's, cut to what it shows; a layer whose frame cannot be had is left as painted, blank.
   */
  void drawCompose(Graphics2D g, Component c) {
    if (ToolkitSupport.isA(c.getClass(), ComposeSupport.LAYER)) {
      try {
        BufferedImage frame = ComposeSupport.screenshot(c);
        if (frame != null) {
          g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
          g.drawImage(frame, 0, 0, c.getWidth(), c.getHeight(), null);
        }
      } catch (LinkageError | RuntimeException e) {
        logger.debug("Session replay could not draw a Compose layer: {}", e.toString());
      }
      return;
    }
    if (c instanceof Container) {
      for (Component child : ((Container) c).getComponents()) {
        if (child.isVisible() && child.getWidth() > 0 && child.getHeight() > 0) {
          Graphics2D inside = (Graphics2D) g.create(child.getX(), child.getY(), child.getWidth(), child.getHeight());
          try {
            drawCompose(inside, child);
          } finally {
            inside.dispose();
          }
        }
      }
    }
  }

  /** What of a window is painted: a Swing window's root pane, else the window itself. */
  static Component content(Window window) {
    return window instanceof RootPaneContainer ? ((RootPaneContainer) window).getRootPane() : window;
  }

  /** The windows a window owns that are showing, such as dialogs and popups, and theirs, in order. */
  static void ownedWindows(Window window, List<Window> out) {
    for (Window owned : window.getOwnedWindows()) {
      if (owned.isShowing()) {
        out.add(owned);
        ownedWindows(owned, out);
      }
    }
  }
}
