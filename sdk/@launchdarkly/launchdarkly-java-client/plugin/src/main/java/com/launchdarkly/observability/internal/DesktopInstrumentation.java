package com.launchdarkly.observability.internal;

import com.launchdarkly.logging.LDLogger;

import java.awt.AWTEvent;
import java.awt.Button;
import java.awt.Component;
import java.awt.Dialog;
import java.awt.Frame;
import java.awt.KeyboardFocusManager;
import java.awt.Label;
import java.awt.Point;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.AWTEventListener;
import java.awt.event.MouseEvent;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import javax.swing.AbstractButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;

/**
 * Watches the application's Swing and AWT windows: which window is active, which tells whether the
 * application is in the foreground and which screen it shows, and mouse clicks. It is the desktop
 * counterpart of the Android plugin's lifecycle tracker, screen-view manager and tap detection.
 * <p>
 * It never starts AWT itself: in an application that has no windows, AWT starting would add a dock
 * icon on macOS and load the windowing libraries for nothing. So it attaches only once AWT is in
 * use, checking every second until then, and a console application costs one cheap check a
 * second. Its listener runs on the event dispatch thread, so it does little there and never throws.
 */
final class DesktopInstrumentation implements AWTEventListener, ToolkitInstrumentation {
  /** Registers the listener with AWT. Replaced in tests. */
  interface Listeners {
    void add(AWTEventListener listener, long mask);

    void remove(AWTEventListener listener);
  }

  static final Listeners TOOLKIT = new Listeners() {
    @Override
    public void add(AWTEventListener listener, long mask) {
      Toolkit.getDefaultToolkit().addAWTEventListener(listener, mask);
    }

    @Override
    public void remove(AWTEventListener listener) {
      Toolkit.getDefaultToolkit().removeAWTEventListener(listener);
    }
  };

  /** How long the application may have no active window before it counts as in the background. */
  static final long BACKGROUND_DELAY_MILLIS = 300;
  /**
   * How long the application may have no active window when the plugin starts, which is often
   * between one window closing and the next opening, such as a login dialog and the main window.
   */
  static final long STARTUP_DELAY_MILLIS = 2000;
  static final long ATTACH_POLL_MILLIS = 1000;
  static final int MAX_PATH_DEPTH = 24;
  static final int MAX_TEXT_LENGTH = 128;

  private final Signals signals;
  private final boolean windows;
  private final boolean clicks;
  private final boolean recordText;
  private final LDLogger logger;
  private final ScheduledExecutorService scheduler;
  private final BooleanSupplier awtInUse;
  private final Supplier<Window> activeWindow;
  private final Listeners listeners;

  private final Object lock = new Object();
  private volatile boolean attached;
  private boolean stopped;
  private boolean foreground;
  private ScheduledFuture<?> pending;
  // The last press, which a click is timed from. Touched only on the event dispatch thread.
  private Component pressedComponent;
  private long pressedAtMillis;

  /**
   * The production instrumentation. A static factory, so that a runtime without AWT fails here, where
   * the caller can catch it, rather than wherever this class is first touched.
   */
  static DesktopInstrumentation create(Signals signals, boolean windows, boolean clicks, boolean recordText,
      LDLogger logger) {
    return new DesktopInstrumentation(signals, windows, clicks, recordText, logger);
  }

  private DesktopInstrumentation(Signals signals, boolean windows, boolean clicks, boolean recordText,
      LDLogger logger) {
    this(signals, windows, clicks, recordText, logger,
        Executors.newSingleThreadScheduledExecutor(DesktopInstrumentation::daemonThread),
        DesktopInstrumentation::awtThreadsRunning,
        () -> KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow(), TOOLKIT);
  }

  DesktopInstrumentation(Signals signals, boolean windows, boolean clicks, boolean recordText, LDLogger logger,
      ScheduledExecutorService scheduler, BooleanSupplier awtInUse, Supplier<Window> activeWindow,
      Listeners listeners) {
    this.signals = signals;
    this.windows = windows;
    this.clicks = clicks;
    this.recordText = recordText;
    this.logger = logger;
    this.scheduler = scheduler;
    this.awtInUse = awtInUse;
    this.activeWindow = activeWindow;
    this.listeners = listeners;
  }

  /** Attaches now if AWT is in use, or as soon as it is. */
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
      }, ATTACH_POLL_MILLIS, ATTACH_POLL_MILLIS, TimeUnit.MILLISECONDS);
    }
  }

  static Thread daemonThread(Runnable runnable) {
    Thread thread = new Thread(runnable, "LaunchDarkly-observability-desktop");
    thread.setDaemon(true);
    return thread;
  }

  /** Thrown from the poll once attached, which is how a fixed-delay task cancels itself. */
  private static final class AttachedException extends RuntimeException {
    AttachedException() {
      super(null, null, false, false);
    }
  }

  private boolean tryAttach() {
    synchronized (lock) {
      if (stopped || attached) {
        return true;
      }
      if (!awtInUse.getAsBoolean()) {
        return false;
      }
      try {
        long mask = (windows ? AWTEvent.WINDOW_EVENT_MASK : 0) | (clicks ? AWTEvent.MOUSE_EVENT_MASK : 0);
        listeners.add(this, mask);
      } catch (RuntimeException | LinkageError e) {
        // Headless, a security manager, or no AWT at all: stop trying.
        logger.debug("Not watching windows and clicks: {}", e.toString());
        stopped = true;
        return true;
      }
      attached = true;
      if (windows) {
        // In the foreground until shown otherwise: starting the plugin is not a transition.
        foreground = true;
        Window active = activeWindow.get();
        if (active != null) {
          showScreen(active);
        } else {
          pending = scheduler.schedule(this::checkStartedInBackground, STARTUP_DELAY_MILLIS, TimeUnit.MILLISECONDS);
        }
      }
      return true;
    }
  }

  /** Detaches and stops checking. */
  @Override
  public void stop() {
    synchronized (lock) {
      stopped = true;
      if (attached) {
        try {
          listeners.remove(this);
        } catch (RuntimeException e) {
          // Shutting down; nothing more to do.
        }
        attached = false;
      }
    }
    scheduler.shutdownNow();
  }

  /** Whether an AWT window is active. Asks AWT only once attached, since asking would start it. */
  @Override
  public boolean hasActiveWindow() {
    return attached && activeWindow.get() != null;
  }

  @Override
  public void eventDispatched(AWTEvent event) {
    try {
      if (event instanceof WindowEvent) {
        onWindowEvent((WindowEvent) event);
      } else if (event instanceof MouseEvent) {
        onMouseEvent((MouseEvent) event);
      }
    } catch (RuntimeException e) {
      logger.debug("Ignoring an error while watching windows and clicks: {}", e.toString());
    }
  }

  private void onWindowEvent(WindowEvent event) {
    if (event.getID() == WindowEvent.WINDOW_ACTIVATED) {
      synchronized (lock) {
        cancelPending();
        if (!foreground) {
          foreground = true;
          signals.foreground();
        }
      }
      showScreen(event.getWindow());
    } else if (event.getID() == WindowEvent.WINDOW_DEACTIVATED && event.getOppositeWindow() == null) {
      // Focus left the application, unless another of its windows is about to become active, which
      // some window managers report as a deactivation to nothing first.
      synchronized (lock) {
        cancelPending();
        pending = scheduler.schedule(this::checkBackground, BACKGROUND_DELAY_MILLIS, TimeUnit.MILLISECONDS);
      }
    }
  }

  void checkStartedInBackground() {
    synchronized (lock) {
      pending = null;
      if (foreground && !stopped && activeWindow.get() == null) {
        foreground = false;
        signals.startedInBackground();
      }
    }
  }

  void checkBackground() {
    synchronized (lock) {
      pending = null;
      if (foreground && !stopped && activeWindow.get() == null) {
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

  private void showScreen(Window window) {
    String title = window instanceof Frame ? ((Frame) window).getTitle()
        : window instanceof Dialog ? ((Dialog) window).getTitle() : null;
    String screenClass = window.getClass().getName();
    // A title can name a person, a document or an account, so it is the screen's name only when the
    // application allows recording text.
    String name = recordText && title != null && !title.trim().isEmpty() ? title : simpleName(window.getClass());
    // Two windows of one class are two screens; the component name AWT gives each window tells them apart.
    String id = window.getName() != null ? screenClass + "#" + window.getName() : screenClass;
    signals.screenShown(name, screenClass, id);
  }

  private void onMouseEvent(MouseEvent event) {
    if (event.getID() == MouseEvent.MOUSE_PRESSED) {
      pressedComponent = event.getComponent();
      pressedAtMillis = event.getWhen();
    } else if (event.getID() == MouseEvent.MOUSE_CLICKED) {
      Component component = event.getComponent();
      long start = component == pressedComponent && pressedAtMillis <= event.getWhen()
          ? pressedAtMillis : event.getWhen();
      pressedComponent = null;
      Click inside = ToolkitSupport.isA(component.getClass(), PivotSupport.HOST)
          ? pivotClick(component, event, start) : composeClick(component, event, start);
      signals.click(inside != null ? inside : new Click(simpleName(component.getClass()), component.getClass().getName(),
          name(component), recordText ? text(component) : null, path(component), event.getXOnScreen(), event.getYOnScreen(),
          start, event.getWhen()));
    }
  }

  /**
   * A click in Apache Pivot, which AWT sees as a click on Pivot's host: the Pivot component under
   * the mouse, placed below the host. Null when this Pivot is too different from the one the plugin
   * knows, so the host is reported instead.
   */
  Click pivotClick(Component host, MouseEvent event, long start) {
    try {
      // Every Pivot type stays inside PivotSupport, so this class never loads one.
      return PivotSupport.click(host, event.getX(), event.getY(), recordText, path(host), event.getXOnScreen(),
          event.getYOnScreen(), start, event.getWhen());
    } catch (LinkageError | RuntimeException e) {
      return null;
    }
  }

  /**
   * A click in Compose for Desktop, which AWT sees as a click on skiko's canvas inside the Compose
   * layer: the composable under the mouse, placed below the layer. Null when the click is not on a
   * layer, no composable is there, or this Compose is too different from the one the plugin knows.
   */
  Click composeClick(Component component, MouseEvent event, long start) {
    Component layer = ToolkitSupport.isA(component.getClass(), ComposeSupport.LAYER) ? component
        : component.getParent() != null && ToolkitSupport.isA(component.getParent().getClass(), ComposeSupport.LAYER)
            ? component.getParent() : null;
    if (layer == null) {
      return null;
    }
    try {
      // Every Compose type stays inside ComposeSupport, so this class never loads one.
      Point at = SwingUtilities.convertPoint(component, event.getX(), event.getY(), layer);
      return ComposeSupport.click(layer, at.x, at.y, recordText, path(layer), event.getXOnScreen(),
          event.getYOnScreen(), start, event.getWhen());
    } catch (LinkageError | RuntimeException e) {
      return null;
    }
  }

  /**
   * The name the application gave a component, or null. Only a Swing component's name is used: AWT
   * names every window and heavyweight component itself ({@code frame0}, {@code button3}), and Swing
   * names its own internal panes {@code null.contentPane} and the like.
   */
  static String name(Component component) {
    String name = component instanceof JComponent ? component.getName() : null;
    return name == null || name.startsWith("null.") ? null : name;
  }

  /**
   * The visible text of a button or label, which describes what was clicked. The text of other
   * components is user data, such as a password or a search, so it is never recorded.
   */
  static String text(Component component) {
    String text = null;
    if (component instanceof AbstractButton) {
      text = ((AbstractButton) component).getText();
    } else if (component instanceof JLabel) {
      text = ((JLabel) component).getText();
    } else if (component instanceof Button) {
      text = ((Button) component).getLabel();
    } else if (component instanceof Label) {
      text = ((Label) component).getText();
    }
    return clip(text);
  }

  /** Text worth recording: not blank, and cut to {@link #MAX_TEXT_LENGTH}; else null. */
  static String clip(String text) {
    if (text == null || text.trim().isEmpty()) {
      return null;
    }
    return text.length() > MAX_TEXT_LENGTH ? text.substring(0, MAX_TEXT_LENGTH) : text;
  }

  /**
   * The component's place in its window, outermost first: {@code JFrame/JRootPane/.../JButton#save},
   * with a component's name after {@code #} when the application gave it one.
   */
  static String path(Component component) {
    List<String> segments = new ArrayList<>();
    for (Component c = component; c != null && segments.size() < MAX_PATH_DEPTH; c = c.getParent()) {
      String segment = simpleName(c.getClass());
      String name = name(c);
      if (name != null) {
        segment += "#" + name;
      }
      segments.add(segment);
      if (c instanceof Window) {
        break;
      }
    }
    Collections.reverse(segments);
    return String.join("/", segments);
  }

  /** The class's simple name, or for an anonymous class its superclass's. */
  static String simpleName(Class<?> type) {
    return ToolkitSupport.simpleName(type);
  }

  /** Whether the application has started AWT. */
  static boolean awtThreadsRunning() {
    return ToolkitSupport.awtInUse();
  }

  /** Whether one of these thread names is a thread AWT starts. */
  static boolean anyAwtThread(Iterable<String> threadNames) {
    return ToolkitSupport.anyAwtThread(threadNames);
  }
}
