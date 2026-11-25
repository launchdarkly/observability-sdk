package com.launchdarkly.observability.internal;

import io.opentelemetry.api.common.Attributes;

import java.util.Map;

/**
 * Hears what the running service records, for instrumentation built on top of observability, such
 * as session replay. Every method has a default that does nothing. Calls come from whatever thread
 * recorded the signal, often the UI thread, so a listener must return quickly and must not throw;
 * the service ignores anything a listener throws.
 */
public interface ObservabilityListener {
  /** The application came to the foreground. */
  default void foreground() {}

  /** The application went to the background. */
  default void background() {}

  /**
   * A screen was shown: a window became active.
   *
   * @param name the screen's name
   * @param screenClass the class behind the screen, or null
   * @param screenId the screen's id, or null
   */
  default void screenShown(String name, String screenClass, String screenId) {}

  /**
   * A component was clicked.
   *
   * @param click the click
   */
  default void click(Click click) {}

  /**
   * A custom event was tracked, through the SDK or {@code LDObserve}.
   *
   * @param key the event key
   * @param metricValue the metric value, or null
   * @param data the event data
   */
  default void track(String key, Double metricValue, Attributes data) {}

  /**
   * An identify completed.
   *
   * @param contextKeys each context kind and its key
   * @param canonicalKey the context's fully qualified key
   */
  default void identified(Map<String, String> contextKeys, String canonicalKey) {}

  /** The service stopped; it records nothing more. */
  default void stopped() {}
}
