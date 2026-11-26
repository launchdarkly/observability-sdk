package com.launchdarkly.observability.internal;

import com.launchdarkly.observability.ObservabilityOptions;

/** Starts observability in memory for tests outside this package, and stops it again. */
public final class TestRuntime {
  public static final String CREDENTIAL = TestTelemetry.CREDENTIAL;
  /** A backend nothing listens on: replay's requests fail at once, as ones that can be retried. */
  public static final String UNREACHABLE_BACKEND = "http://127.0.0.1:1"; // DevSkim: ignore DS162092

  private static TestTelemetry telemetry;

  private TestRuntime() {}

  /** From now on, observability starts in memory. */
  public static void useMemory() {
    telemetry = new TestTelemetry();
    ObservabilityRuntime.setFactoryForTest(telemetry.factory());
  }

  /** Stops observability and restores the real factory. */
  public static void reset() {
    ObservabilityRuntime.resetForTest();
  }

  /** Options for a quiet test service whose replay sends nowhere. */
  public static ObservabilityOptions.Builder quietOptions() {
    return TestTelemetry.quietOptions().backendUrl(UNREACHABLE_BACKEND);
  }

  /** What the service has logged, as text. */
  public static String logs() {
    return telemetry.logCapture.getMessageStrings().toString();
  }
}
