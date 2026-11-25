package com.launchdarkly.observability.internal;

import org.junit.Assume;

import java.awt.GraphicsEnvironment;
import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Starts JavaFX once for the test JVM, or skips the test that needs it. Refers to JavaFX only by
 * name, so a test class can call it on Java 8, where JavaFX's classes cannot load.
 */
public final class FxAssume {
  private static Boolean started;

  private FxAssume() {}

  /** Skips the calling test unless JavaFX runs: Java 11 or later, a display, and a toolkit that starts. */
  public static void assumeJavaFx() {
    Assume.assumeFalse("JavaFX needs Java 11 or later", System.getProperty("java.specification.version").startsWith("1."));
    Assume.assumeFalse("needs a display", GraphicsEnvironment.isHeadless());
    Assume.assumeTrue("JavaFX did not start", start());
  }

  private static synchronized boolean start() {
    if (started == null) {
      started = startToolkit();
    }
    return started;
  }

  private static boolean startToolkit() {
    try {
      Class<?> platform = Class.forName("javafx.application.Platform");
      // Closing a test's last window must not end JavaFX for the tests after it.
      platform.getMethod("setImplicitExit", boolean.class).invoke(null, false);
      CountDownLatch ready = new CountDownLatch(1);
      try {
        platform.getMethod("startup", Runnable.class).invoke(null, (Runnable) ready::countDown);
      } catch (InvocationTargetException e) {
        if (!(e.getCause() instanceof IllegalStateException)) { // already started
          return false;
        }
        ready.countDown();
      }
      return ready.await(10, TimeUnit.SECONDS);
    } catch (ReflectiveOperationException | LinkageError e) {
      return false;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return false;
    }
  }
}
