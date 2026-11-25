package com.launchdarkly.observability.internal;

import org.eclipse.swt.SWT;
import org.eclipse.swt.SWTError;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.junit.Assume;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.function.BooleanSupplier;

/**
 * SWT on screen, for the tests, which run on the JVM's main thread, SWT's: one display for them all,
 * and shells with controls at fixed places.
 */
public final class SwtFixture {
  private static Display display;

  private SwtFixture() {}

  /** The display. Skips the test where SWT cannot open one, as without a screen. */
  public static Display display() {
    if (display == null) {
      try {
        display = new Display();
      } catch (SWTError | UnsatisfiedLinkError e) {
        Assume.assumeNoException("SWT cannot open a display here", e);
      }
    }
    return display;
  }

  /** An open shell, its client area this size, with no layout, so controls stay where they are put. */
  public static Shell shell(int width, int height) {
    Shell shell = new Shell(display(), SWT.SHELL_TRIM);
    shell.setLocation(120, 120);
    shell.setSize(shell.computeTrim(0, 0, width, height).width, shell.computeTrim(0, 0, width, height).height);
    shell.open();
    pump(200);
    return shell;
  }

  /** The control, at this place in its parent. */
  public static <T extends Control> T at(T control, int x, int y, int width, int height) {
    control.setBounds(x, y, width, height);
    return control;
  }

  /** Dispatches SWT's events for a while. */
  public static void pump(long millis) {
    long end = System.currentTimeMillis() + millis;
    while (System.currentTimeMillis() < end) {
      idle();
    }
  }

  /** Dispatches SWT's events until the condition holds, for up to ten seconds. */
  public static void pumpUntil(BooleanSupplier condition) {
    long end = System.currentTimeMillis() + 10_000;
    while (!condition.getAsBoolean()) {
      if (System.currentTimeMillis() > end) {
        throw new AssertionError("timed out");
      }
      idle();
    }
  }

  /** Runs the task on another thread while this one, SWT's, dispatches, and returns what it returns. */
  public static <T> T offThread(Callable<T> task) throws Exception {
    FutureTask<T> future = new FutureTask<>(task);
    Thread thread = new Thread(future, "test-off-swt");
    thread.start();
    pumpUntil(future::isDone);
    try {
      return future.get();
    } catch (ExecutionException e) {
      throw (Exception) e.getCause();
    }
  }

  /** Dispatches an event, or waits in SWT's loop, as an application's thread does, for one or 5 ms. */
  private static void idle() {
    Display d = display();
    if (!d.readAndDispatch()) {
      d.timerExec(5, () -> { });
      d.sleep();
    }
  }
}
