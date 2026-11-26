package com.launchdarkly.observability.internal;

import com.launchdarkly.logging.LDLogger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Predicate;

import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.collections.ListChangeListener;
import javafx.event.EventHandler;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Control;
import javafx.scene.control.Labeled;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.PickResult;
import javafx.stage.PopupWindow;
import javafx.stage.Stage;
import javafx.stage.Window;

/**
 * Watches the application's JavaFX windows, as {@link DesktopInstrumentation} watches its Swing and
 * AWT ones: which window has focus, which tells whether the application is in the foreground and
 * which screen it shows, and mouse clicks. Popups such as menus and tooltips are not screens, but
 * clicks in them count.
 * <p>
 * It never starts JavaFX: it attaches once the JavaFX application thread exists, checking every
 * second until then. Everything it does with JavaFX runs on that thread. Loaded only through
 * {@link FxSupport}, since it needs JavaFX's classes.
 */
final class FxInstrumentation implements ToolkitInstrumentation {
  static final String FX_THREAD_NAME = "JavaFX Application Thread";

  private final Signals signals;
  private final boolean windows;
  private final boolean clicks;
  private final boolean recordText;
  private final LDLogger logger;
  private final ScheduledExecutorService scheduler;
  private final BooleanSupplier fxInUse;
  // Replaced in tests: JavaFX's queue, which refuses work before it starts and after it exits, and
  // whether a window has focus, which a test cannot set.
  Consumer<Runnable> runLater = Platform::runLater;
  Predicate<Window> isFocused = Window::isFocused;

  private final Object lock = new Object();
  private boolean attaching;
  private boolean stopped;
  private boolean foreground;
  private ScheduledFuture<?> pending;
  private volatile boolean attached;
  private volatile Window focused;

  // Touched only on the JavaFX application thread.
  final Map<Window, Watch> watched = new IdentityHashMap<>();
  private ListChangeListener<Window> windowList;
  private Node pressedNode;
  private long pressedAtMillis;

  /** The production instrumentation, which {@link FxSupport} calls by name. */
  static ToolkitInstrumentation create(Signals signals, boolean windows, boolean clicks, boolean recordText,
      LDLogger logger) {
    return new FxInstrumentation(signals, windows, clicks, recordText, logger,
        Executors.newSingleThreadScheduledExecutor(r -> {
          Thread thread = new Thread(r, "LaunchDarkly-observability-javafx");
          thread.setDaemon(true);
          return thread;
        }),
        FxInstrumentation::fxThreadRunning);
  }

  FxInstrumentation(Signals signals, boolean windows, boolean clicks, boolean recordText, LDLogger logger,
      ScheduledExecutorService scheduler, BooleanSupplier fxInUse) {
    this.signals = signals;
    this.windows = windows;
    this.clicks = clicks;
    this.recordText = recordText;
    this.logger = logger;
    this.scheduler = scheduler;
    this.fxInUse = fxInUse;
  }

  @Override
  public void start() {
    if (!windows && !clicks) {
      return;
    }
    if (!tryAttach()) {
      scheduler.scheduleWithFixedDelay(() -> {
        if (tryAttach()) {
          throw new AttachedException(); // ends the schedule
        }
      }, DesktopInstrumentation.ATTACH_POLL_MILLIS, DesktopInstrumentation.ATTACH_POLL_MILLIS, TimeUnit.MILLISECONDS);
    }
  }

  /** Thrown from the poll once attached, which is how a fixed-delay task cancels itself. */
  private static final class AttachedException extends RuntimeException {
    AttachedException() {
      super(null, null, false, false);
    }
  }

  private boolean tryAttach() {
    synchronized (lock) {
      if (stopped || attaching) {
        return true;
      }
      if (!fxInUse.getAsBoolean()) {
        return false;
      }
      try {
        runLater.accept(this::attach);
      } catch (IllegalStateException e) {
        // The toolkit is starting or has exited; try again on the next poll.
        return false;
      }
      attaching = true;
      return true;
    }
  }

  /** Watches every window, and every window opened from now on. On the JavaFX thread. */
  void attach() {
    synchronized (lock) {
      if (stopped) {
        return;
      }
    }
    windowList = change -> {
      while (change.next()) {
        change.getRemoved().forEach(this::unwatch);
        change.getAddedSubList().forEach(this::watch);
      }
    };
    Window.getWindows().addListener(windowList);
    new ArrayList<>(Window.getWindows()).forEach(this::watch);
    attached = true;
    if (windows) {
      Window active = null;
      for (Window window : watched.keySet()) {
        if (isFocused.test(window) && !(window instanceof PopupWindow)) {
          active = window;
        }
      }
      synchronized (lock) {
        // In the foreground until shown otherwise: starting the plugin is not a transition.
        foreground = true;
        if (active == null) {
          pending = scheduler.schedule(this::checkStartedInBackground,
              DesktopInstrumentation.STARTUP_DELAY_MILLIS, TimeUnit.MILLISECONDS);
        }
      }
      if (active != null) {
        focused = active;
        showScreen(active);
      }
    }
  }

  @Override
  public void stop() {
    boolean detach;
    synchronized (lock) {
      stopped = true;
      detach = attaching;
    }
    scheduler.shutdownNow();
    if (detach) {
      try {
        runLater.accept(this::detach);
      } catch (IllegalStateException e) {
        // JavaFX has exited, and its windows with it.
      }
    }
  }

  /** Stops watching. On the JavaFX thread. */
  void detach() {
    if (windowList != null) {
      Window.getWindows().removeListener(windowList);
    }
    new ArrayList<>(watched.keySet()).forEach(this::unwatch);
    attached = false;
    focused = null;
  }

  @Override
  public boolean hasActiveWindow() {
    return attached && focused != null;
  }

  /** The listeners on one window, kept so they can be removed. */
  final class Watch {
    final ChangeListener<Boolean> focus;
    final EventHandler<MouseEvent> mouse = FxInstrumentation.this::onMouse;

    Watch(Window window) {
      focus = (property, was, is) -> onFocus(window, is);
    }
  }

  void watch(Window window) {
    if (watched.containsKey(window)) {
      return;
    }
    Watch watch = new Watch(window);
    watched.put(window, watch);
    if (windows && !(window instanceof PopupWindow)) {
      window.focusedProperty().addListener(watch.focus);
    }
    if (clicks) {
      window.addEventFilter(MouseEvent.MOUSE_PRESSED, watch.mouse);
      window.addEventFilter(MouseEvent.MOUSE_CLICKED, watch.mouse);
    }
  }

  void unwatch(Window window) {
    Watch watch = watched.remove(window);
    if (watch == null) {
      return;
    }
    window.focusedProperty().removeListener(watch.focus);
    window.removeEventFilter(MouseEvent.MOUSE_PRESSED, watch.mouse);
    window.removeEventFilter(MouseEvent.MOUSE_CLICKED, watch.mouse);
    if (focused == window) {
      focused = null;
    }
  }

  void onFocus(Window window, boolean isFocused) {
    try {
      if (isFocused) {
        focused = window;
        synchronized (lock) {
          cancelPending();
          if (!foreground) {
            foreground = true;
            signals.foreground();
          }
        }
        showScreen(window);
      } else {
        if (focused == window) {
          focused = null;
        }
        // Focus moving to another of the application's windows arrives as a loss, then a gain.
        synchronized (lock) {
          cancelPending();
          pending = scheduler.schedule(this::checkBackground, DesktopInstrumentation.BACKGROUND_DELAY_MILLIS,
              TimeUnit.MILLISECONDS);
        }
      }
    } catch (RuntimeException e) {
      logger.debug("Ignoring an error while watching JavaFX windows: {}", e.toString());
    }
  }

  void checkStartedInBackground() {
    synchronized (lock) {
      pending = null;
      if (foreground && !stopped && focused == null) {
        foreground = false;
        signals.startedInBackground();
      }
    }
  }

  void checkBackground() {
    synchronized (lock) {
      pending = null;
      if (foreground && !stopped && focused == null) {
        foreground = false;
        signals.background();
      }
    }
  }

  private void cancelPending() {
    if (pending != null) {
      pending.cancel(false);
      pending = null;
    }
  }

  /**
   * Reports the window as the screen shown. A JavaFX window is nearly always a plain {@code Stage},
   * so the screen is named after the scene's root, which is usually the application's own class,
   * and told apart by the root's id.
   */
  private void showScreen(Window window) {
    Scene scene = window.getScene();
    Parent root = scene == null ? null : scene.getRoot();
    Class<?> type = root != null ? root.getClass() : window.getClass();
    String title = window instanceof Stage ? ((Stage) window).getTitle() : null;
    // A title can name a person, a document or an account, so it is the screen's name only when the
    // application allows recording text.
    String name = recordText && title != null && !title.trim().isEmpty() ? title : ToolkitSupport.simpleName(type);
    String rootId = root == null ? null : root.getId();
    String screenClass = type.getName();
    signals.screenShown(name, screenClass, rootId != null ? screenClass + "#" + rootId : screenClass);
  }

  void onMouse(MouseEvent event) {
    try {
      long now = System.currentTimeMillis();
      if (event.getEventType() == MouseEvent.MOUSE_PRESSED) {
        pressedNode = target(event);
        pressedAtMillis = now;
      } else if (event.getEventType() == MouseEvent.MOUSE_CLICKED) {
        Node node = target(event);
        if (node == null) {
          return;
        }
        long start = node == pressedNode && pressedAtMillis <= now ? pressedAtMillis : now;
        pressedNode = null;
        signals.click(new Click(ToolkitSupport.simpleName(node.getClass()), node.getClass().getName(), node.getId(),
            recordText ? text(node) : null, path(node), (long) event.getScreenX(), (long) event.getScreenY(),
            start, now));
      }
    } catch (RuntimeException e) {
      logger.debug("Ignoring an error while watching JavaFX clicks: {}", e.toString());
    }
  }

  /**
   * What was clicked: the control under the mouse, since what JavaFX picks is often a part of the
   * control's skin, such as the text inside a button.
   */
  static Node target(MouseEvent event) {
    PickResult pick = event.getPickResult(); // never null: JavaFX makes one when the event has none
    Node node = pick.getIntersectedNode();
    for (Node n = node; n != null; n = n.getParent()) {
      if (n instanceof Control) {
        return n;
      }
    }
    return node;
  }

  /**
   * The visible text of a labeled control, such as a button or label. The text of other nodes is
   * user data, such as a password or a search, so it is never recorded.
   */
  static String text(Node node) {
    return node instanceof Labeled ? ToolkitSupport.visibleText(((Labeled) node).getText()) : null;
  }

  /** The node's place in its scene, outermost first: {@code BorderPane/VBox/Button#save}. */
  static String path(Node node) {
    List<String> segments = new ArrayList<>();
    for (Node n = node; n != null && segments.size() < ToolkitSupport.MAX_PATH_DEPTH; n = n.getParent()) {
      String segment = ToolkitSupport.simpleName(n.getClass());
      if (n.getId() != null) {
        segment += "#" + n.getId();
      }
      segments.add(segment);
    }
    Collections.reverse(segments);
    return String.join("/", segments);
  }

  /** Whether the application has started JavaFX, told from its threads. */
  static boolean fxThreadRunning() {
    return ToolkitSupport.threadNames().contains(FX_THREAD_NAME);
  }
}
