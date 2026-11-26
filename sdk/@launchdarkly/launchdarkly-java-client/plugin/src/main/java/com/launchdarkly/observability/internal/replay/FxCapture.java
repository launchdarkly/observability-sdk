package com.launchdarkly.observability.internal.replay;

import com.launchdarkly.logging.LDLogger;
import com.launchdarkly.observability.internal.ToolkitSupport;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Predicate;

import javafx.application.Platform;
import javafx.event.EventHandler;
import javafx.event.EventType;
import javafx.scene.Scene;
import javafx.scene.input.MouseEvent;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;
import javafx.stage.PopupWindow;
import javafx.stage.Stage;
import javafx.stage.Window;

/**
 * Captures the focused JavaFX window. One task on the JavaFX application thread snapshots the
 * window's scene, then the stages and popups it owns, each at its place over it. Each is masked as
 * soon as it is copied in, before the next goes over it, so a dialog over a masked node stays
 * visible. Nothing renders in between, so the masks are where the pixels are. The frame handed on
 * is already masked.
 * <p>
 * A snapshot renders the scene, which costs several milliseconds on the application thread. Each
 * captured scene has a pulse listener, which tells whether JavaFX laid out anything since the last
 * frame; when it has not, and the window is the same, the frame is skipped without a snapshot.
 * <p>
 * Each captured scene also reports the mouse in it to the pointer sink, if there is one.
 * <p>
 * Loaded only when JavaFX is present, through {@link #create}.
 */
final class FxCapture implements CaptureSource {
  private final double scale;
  private final FxMasks masks;
  private final LDLogger logger;
  private final BooleanSupplier fxInUse;
  // Replaced in tests: JavaFX's queue, and whether a window has focus, which a test cannot set.
  Consumer<Runnable> runLater = Platform::runLater;
  Predicate<Window> isFocused = Window::isFocused;
  /** Where the mouse is reported, or null. Set before the first capture. */
  volatile PointerSink pointer;

  // Touched only on the JavaFX thread.
  private final Map<Scene, Boolean> watched = new WeakHashMap<>();
  private final AtomicBoolean changed = new AtomicBoolean(true);
  private Window lastBase;
  private double lastWidth;
  private double lastHeight;
  private final Runnable onPulse = () -> changed.set(true);
  private final EventHandler<MouseEvent> onMouse = this::mouseEvent;

  /** The production capture, called by name, so this class loads only with JavaFX. */
  static CaptureSource create(double scale, MaskRules rules, LDLogger logger, PointerSink pointer) {
    FxCapture capture = new FxCapture(scale, new FxMasks(rules), logger, ToolkitSupport::fxInUse);
    capture.pointer = pointer;
    return capture;
  }

  FxCapture(double scale, FxMasks masks, LDLogger logger, BooleanSupplier fxInUse) {
    this.scale = scale;
    this.masks = masks;
    this.logger = logger;
    this.fxInUse = fxInUse;
  }

  /**
   * A snapshot taken on the JavaFX thread: the pixels at scale 1, already masked, the masks applied,
   * and where on the screen the scene is.
   */
  static final class Painted {
    final int[] pixels;
    final int width;
    final int height;
    final List<Rectangle> masks;
    final double originX;
    final double originY;

    Painted(int[] pixels, int width, int height, List<Rectangle> masks, double originX, double originY) {
      this.pixels = pixels;
      this.width = width;
      this.height = height;
      this.masks = masks;
      this.originX = originX;
      this.originY = originY;
    }
  }

  @Override
  public RawFrame capture(long timeoutMillis) {
    if (!fxInUse.getAsBoolean()) {
      return null; // touching JavaFX before the application does would start it
    }
    FutureTask<Painted> task = new FutureTask<>(this::paint);
    try {
      runLater.accept(task);
    } catch (IllegalStateException e) {
      return null; // JavaFX is starting or has exited
    }
    Painted painted;
    try {
      painted = task.get(timeoutMillis, TimeUnit.MILLISECONDS);
    } catch (TimeoutException e) {
      task.cancel(false);
      return null;
    } catch (ExecutionException e) {
      logger.debug("Session replay could not snapshot a JavaFX window: {}", e.getCause().toString());
      return null;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return null;
    }
    if (painted == null) {
      return null;
    }
    BufferedImage image = toImage(painted);
    return new RawFrame(image, Collections.<Rectangle>emptyList(), System.currentTimeMillis(), painted.originX,
        painted.originY, image.getWidth() / (double) painted.width);
  }

  /** The snapshot, or null if there is no focused window or nothing changed. On the JavaFX thread. */
  Painted paint() {
    Window focused = null;
    for (Window window : Window.getWindows()) {
      if (isFocused.test(window) && !(window instanceof PopupWindow)) {
        focused = window;
      }
    }
    if (focused == null || focused.getScene() == null) {
      return null;
    }
    Window base = focused;
    // A dialog is shown over the window it belongs to, so the frame is that window, with the dialog on it.
    for (Window owner = owner(base); owner != null && owner.isShowing(); owner = owner(base)) {
      base = owner;
    }
    Scene scene = base.getScene();
    double width = scene.getWidth();
    double height = scene.getHeight();
    List<Window> over = new ArrayList<>();
    for (Window window : Window.getWindows()) {
      // Every listed window is showing.
      if (window != base && window.getScene() != null && ownedBy(window, base)) {
        over.add(window);
      }
    }
    for (Window window : over) {
      watch(window.getScene());
    }
    watch(scene);
    boolean same = base == lastBase && width == lastWidth && height == lastHeight;
    if (!changed.getAndSet(false) && same) {
      return null;
    }
    lastBase = base;
    lastWidth = width;
    lastHeight = height;

    // An empty scene cannot be snapshotted; the failure skips the frame.
    int w = (int) Math.ceil(width);
    int h = (int) Math.ceil(height);
    int[] pixels = new int[w * h];
    read(scene.snapshot(null), pixels, w, h, 0, 0);
    List<Rectangle> maskRects = new ArrayList<>();
    layerMasks(scene, 0, 0, pixels, w, h, maskRects);
    // The focused window goes on top of the others the base owns.
    if (over.remove(focused)) {
      over.add(focused);
    }
    double originX = base.getX() + scene.getX();
    double originY = base.getY() + scene.getY();
    for (Window window : over) {
      Scene s = window.getScene();
      int x = (int) Math.round(window.getX() + s.getX() - originX);
      int y = (int) Math.round(window.getY() + s.getY() - originY);
      read(s.snapshot(null), pixels, w, h, x, y);
      layerMasks(s, x, y, pixels, w, h, maskRects);
    }
    return new Painted(pixels, w, h, maskRects, originX, originY);
  }

  /** Masks one scene, drawn at its place, in the pixels at scale 1, before anything goes over it. */
  private void layerMasks(Scene scene, int x, int y, int[] pixels, int w, int h, List<Rectangle> applied) {
    List<Rectangle> layer = new ArrayList<>();
    masks.collect(scene.getRoot(), scene.getWidth(), scene.getHeight(), x, y, 1, layer);
    Pixels.mask(pixels, w, h, layer);
    applied.addAll(layer);
  }

  /** Whether a window belongs to the base: a stage it owns, or a popup shown from it, directly or not. */
  static boolean ownedBy(Window window, Window base) {
    for (Window w = owner(window); w != null; w = owner(w)) {
      if (w == base) {
        return true;
      }
    }
    return false;
  }

  private static Window owner(Window window) {
    if (window instanceof PopupWindow) {
      return ((PopupWindow) window).getOwnerWindow();
    }
    return window instanceof Stage ? ((Stage) window).getOwner() : null;
  }

  private void watch(Scene scene) {
    if (watched.put(scene, Boolean.TRUE) == null) {
      scene.addPostLayoutPulseListener(onPulse);
      scene.addEventFilter(MouseEvent.ANY, onMouse);
    }
  }

  /** Takes the listeners off the scenes. */
  @Override
  public void stop() {
    if (!fxInUse.getAsBoolean()) {
      return;
    }
    try {
      runLater.accept(() -> {
        for (Scene scene : watched.keySet()) {
          scene.removePostLayoutPulseListener(onPulse);
          scene.removeEventFilter(MouseEvent.ANY, onMouse);
        }
        watched.clear();
      });
    } catch (IllegalStateException e) {
      // JavaFX has exited, and its scenes with it.
    }
  }

  /** Reports a press, release or move to the pointer sink. On the JavaFX thread. */
  void mouseEvent(MouseEvent event) {
    PointerSink sink = pointer;
    ReplayItem.Pointer.Action action = action(event.getEventType());
    if (sink != null && action != null) {
      sink.pointer(action, event.getScreenX(), event.getScreenY(), System.currentTimeMillis());
    }
  }

  static ReplayItem.Pointer.Action action(EventType<? extends MouseEvent> type) {
    if (type == MouseEvent.MOUSE_PRESSED) {
      return ReplayItem.Pointer.Action.DOWN;
    }
    if (type == MouseEvent.MOUSE_RELEASED) {
      return ReplayItem.Pointer.Action.UP;
    }
    return type == MouseEvent.MOUSE_MOVED || type == MouseEvent.MOUSE_DRAGGED ? ReplayItem.Pointer.Action.MOVE : null;
  }

  /** Copies a snapshot into the frame's pixels at its place, cut to the frame. */
  static void read(WritableImage snapshot, int[] into, int width, int height, int x, int y) {
    int left = Math.max(0, x);
    int top = Math.max(0, y);
    int right = Math.min(width, x + (int) snapshot.getWidth());
    int bottom = Math.min(height, y + (int) snapshot.getHeight());
    if (right <= left || bottom <= top) {
      return;
    }
    snapshot.getPixelReader().getPixels(left - x, top - y, right - left, bottom - top,
        PixelFormat.getIntArgbInstance(), into, top * width + left, width);
  }

  /** The pixels as the frame's image, scaled off the JavaFX thread. */
  BufferedImage toImage(Painted painted) {
    return Pixels.image(painted.pixels, painted.width, painted.height, scale);
  }

  /** For tests: the scenes with a pulse listener. */
  int watchedScenes() {
    return Collections.unmodifiableMap(watched).size();
  }
}
