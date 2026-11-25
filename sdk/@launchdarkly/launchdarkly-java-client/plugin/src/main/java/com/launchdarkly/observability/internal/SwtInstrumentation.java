package com.launchdarkly.observability.internal;

import com.launchdarkly.logging.LDLogger;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Shell;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Watches the application's SWT shells, as {@link DesktopInstrumentation} watches its Swing and AWT
 * windows: which shell is active, which tells whether the application is in the foreground and which
 * screen it shows, and mouse clicks. SWT has no click event, so a click is a release on the control
 * the button was pressed on, inside it.
 * <p>
 * It never starts SWT: it attaches once SWT's event loop runs, checking every second until then.
 * Everything it does with SWT runs on SWT's thread. Loaded only through {@link FxSupport#addTo}, by
 * name, since it needs SWT's classes.
 */
final class SwtInstrumentation implements ToolkitInstrumentation {
  private final Signals signals;
  private final boolean windows;
  private final boolean clicks;
  private final boolean recordText;
  private final LDLogger logger;
  private final ScheduledExecutorService scheduler;
  private final Supplier<Display> findDisplay;
  // Replaced in tests: the active shell, which a test cannot make active.
  Function<Display, Shell> activeShell = Display::getActiveShell;

  private final Object lock = new Object();
  private boolean stopped;
  private boolean foreground;
  private ScheduledFuture<?> pending;
  private volatile Display display;
  private volatile Shell active;

  // Touched only on SWT's thread.
  private final Listener onActivation = this::onActivation;
  private final Listener onMouse = this::onMouse;
  private Control pressed;
  private long pressedAtMillis;

  /** The production instrumentation, which {@link FxSupport} calls by name. */
  static ToolkitInstrumentation create(Signals signals, boolean windows, boolean clicks, boolean recordText,
      LDLogger logger) {
    return new SwtInstrumentation(signals, windows, clicks, recordText, logger,
        Executors.newSingleThreadScheduledExecutor(r -> {
          Thread thread = new Thread(r, "LaunchDarkly-observability-swt");
          thread.setDaemon(true);
          return thread;
        }),
        SwtSupport::display);
  }

  SwtInstrumentation(Signals signals, boolean windows, boolean clicks, boolean recordText, LDLogger logger,
      ScheduledExecutorService scheduler, Supplier<Display> findDisplay) {
    this.signals = signals;
    this.windows = windows;
    this.clicks = clicks;
    this.recordText = recordText;
    this.logger = logger;
    this.scheduler = scheduler;
    this.findDisplay = findDisplay;
  }

  @Override
  public void start() {
    if (!windows && !clicks) {
      return;
    }
    scheduler.scheduleWithFixedDelay(() -> {
      if (tryAttach()) {
        throw new AttachedException(); // ends the schedule
      }
    }, 0, DesktopInstrumentation.ATTACH_POLL_MILLIS, TimeUnit.MILLISECONDS);
  }

  /** Thrown from the poll once attached, which is how a fixed-delay task cancels itself. */
  private static final class AttachedException extends RuntimeException {
    AttachedException() {
      super(null, null, false, false);
    }
  }

  /** Whether the poll is done: attaching, or stopped. */
  boolean tryAttach() {
    synchronized (lock) {
      if (stopped) {
        return true;
      }
    }
    Display found = findDisplay.get();
    if (found == null) {
      return false;
    }
    try {
      found.asyncExec(() -> attach(found));
    } catch (RuntimeException e) {
      return false; // disposed in between; look again
    }
    return true;
  }

  /** Listens to the display, and reports the shell active now. On SWT's thread, the display's. */
  void attach(Display display) {
    synchronized (lock) {
      if (stopped) {
        return;
      }
      this.display = display;
    }
    if (windows) {
      display.addFilter(SWT.Activate, onActivation);
      display.addFilter(SWT.Deactivate, onActivation);
    }
    if (clicks) {
      display.addFilter(SWT.MouseDown, onMouse);
      display.addFilter(SWT.MouseUp, onMouse);
    }
    if (windows) {
      Shell shell = activeShell.apply(display);
      synchronized (lock) {
        // In the foreground until shown otherwise: starting the plugin is not a transition.
        foreground = true;
        if (shell == null) {
          pending = scheduler.schedule(this::checkStartedInBackground, DesktopInstrumentation.STARTUP_DELAY_MILLIS,
              TimeUnit.MILLISECONDS);
        }
      }
      if (shell != null) {
        active = shell;
        showScreen(shell);
      }
    }
  }

  @Override
  public void stop() {
    Display attached;
    synchronized (lock) {
      stopped = true;
      attached = display;
    }
    scheduler.shutdownNow();
    if (attached != null) {
      try {
        attached.asyncExec(() -> detach(attached));
      } catch (RuntimeException e) {
        // SWT has exited, and its listeners with it.
      }
    }
  }

  /** Stops listening. On SWT's thread, the display's, which runs nothing once disposed. */
  void detach(Display display) {
    display.removeFilter(SWT.Activate, onActivation);
    display.removeFilter(SWT.Deactivate, onActivation);
    display.removeFilter(SWT.MouseDown, onMouse);
    display.removeFilter(SWT.MouseUp, onMouse);
    active = null;
  }

  @Override
  public boolean hasActiveWindow() {
    return active != null;
  }

  /** A shell becoming active, or no longer. Controls are activated too; only shells count. */
  void onActivation(Event event) {
    if (!(event.widget instanceof Shell)) {
      return;
    }
    Shell shell = (Shell) event.widget;
    try {
      if (event.type == SWT.Activate) {
        active = shell;
        synchronized (lock) {
          cancelPending();
          if (!foreground) {
            foreground = true;
            signals.foreground();
          }
        }
        showScreen(shell);
      } else {
        if (active == shell) {
          active = null;
        }
        // Activation moving to another of the application's shells arrives as a loss, then a gain.
        synchronized (lock) {
          cancelPending();
          pending = scheduler.schedule(this::checkBackground, DesktopInstrumentation.BACKGROUND_DELAY_MILLIS,
              TimeUnit.MILLISECONDS);
        }
      }
    } catch (RuntimeException e) {
      logger.debug("Ignoring an error while watching SWT shells: {}", e.toString());
    }
  }

  void checkStartedInBackground() {
    synchronized (lock) {
      pending = null;
      if (foreground && !stopped && active == null) {
        foreground = false;
        signals.startedInBackground();
      }
    }
  }

  void checkBackground() {
    synchronized (lock) {
      pending = null;
      if (foreground && !stopped && active == null) {
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
   * Reports the shell as the screen shown, named after the JFace window it belongs to, if it does,
   * or with {@code recordText} its title, and told apart by its name.
   */
  private void showScreen(Shell shell) {
    Class<?> type = SwtSupport.screenClass(shell);
    String title = shell.getText(); // never null
    // A title can name a person, a document or an account, so it is the screen's name only when the
    // application allows recording text.
    String name = recordText && !title.trim().isEmpty() ? title : ToolkitSupport.simpleName(type);
    String shellName = SwtSupport.name(shell);
    String screenClass = type.getName();
    signals.screenShown(name, screenClass, shellName != null ? screenClass + "#" + shellName : screenClass);
  }

  void onMouse(Event event) {
    if (!(event.widget instanceof Control)) {
      return; // a menu or a tray item
    }
    Control control = (Control) event.widget;
    try {
      long now = System.currentTimeMillis();
      if (event.type == SWT.MouseDown) {
        pressed = control;
        pressedAtMillis = now;
        return;
      }
      boolean click = control == pressed;
      pressed = null;
      Point size = control.getSize();
      if (!click || event.x < 0 || event.y < 0 || event.x >= size.x || event.y >= size.y) {
        return; // released elsewhere, as when dragging
      }
      Point screen = control.getDisplay().map(control, null, event.x, event.y);
      signals.click(new Click(ToolkitSupport.simpleName(control.getClass()), control.getClass().getName(),
          SwtSupport.name(control), recordText ? SwtSupport.text(control) : null, SwtSupport.path(control), screen.x,
          screen.y, pressedAtMillis, now));
    } catch (RuntimeException e) {
      logger.debug("Ignoring an error while watching SWT clicks: {}", e.toString());
    }
  }
}
