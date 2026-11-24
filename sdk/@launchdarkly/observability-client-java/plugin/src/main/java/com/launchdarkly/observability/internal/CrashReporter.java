package com.launchdarkly.observability.internal;

import java.io.PrintStream;
import java.util.function.BiConsumer;

/**
 * Records exceptions that no code caught, on any thread, Swing's event dispatch thread included.
 * Installed as the JVM's default uncaught-exception handler. It hands each exception on to the
 * handler that was installed before it, so the application's own handling still runs; with none,
 * it prints the exception as the JVM would have.
 */
final class CrashReporter implements Thread.UncaughtExceptionHandler {
  // Dropped on uninstall: a handler installed after this one keeps this one alive, and must not keep
  // the stopped service alive through it.
  private volatile BiConsumer<Thread, Throwable> recorder;
  private final Thread.UncaughtExceptionHandler previous;
  private final PrintStream err;

  private CrashReporter(BiConsumer<Thread, Throwable> recorder, Thread.UncaughtExceptionHandler previous,
      PrintStream err) {
    this.recorder = recorder;
    this.previous = previous;
    this.err = err;
  }

  /** Installs a reporter in front of the current default handler. */
  static CrashReporter install(BiConsumer<Thread, Throwable> recorder) {
    return install(recorder, System.err);
  }

  static CrashReporter install(BiConsumer<Thread, Throwable> recorder, PrintStream err) {
    synchronized (CrashReporter.class) {
      CrashReporter reporter = new CrashReporter(recorder, Thread.getDefaultUncaughtExceptionHandler(), err);
      Thread.setDefaultUncaughtExceptionHandler(reporter);
      return reporter;
    }
  }

  /**
   * Restores the handler this one replaced, unless something has replaced this one since, in which
   * case that handler is left alone and this one simply stops recording.
   */
  void uninstall() {
    synchronized (CrashReporter.class) {
      if (Thread.getDefaultUncaughtExceptionHandler() == this) {
        Thread.setDefaultUncaughtExceptionHandler(previous);
      }
    }
    recorder = null;
  }

  @Override
  public void uncaughtException(Thread thread, Throwable error) {
    BiConsumer<Thread, Throwable> r = recorder;
    if (r != null) {
      try {
        r.accept(thread, error);
      } catch (Throwable ignored) {
        // Reporting must never stop the exception reaching the application's handler.
      }
    }
    if (previous != null) {
      previous.uncaughtException(thread, error);
    } else {
      // What ThreadGroup.uncaughtException prints. Calling it would come back here, since it asks
      // the default handler first.
      err.print("Exception in thread \"" + thread.getName() + "\" ");
      error.printStackTrace(err);
    }
  }
}
