package com.launchdarkly.observability;

import com.launchdarkly.observability.internal.AttributeConversion;
import com.launchdarkly.observability.internal.GlobalAttributes;
import com.launchdarkly.observability.internal.ObservabilityRuntime;
import com.launchdarkly.observability.internal.ObservabilityService;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.logs.Severity;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Records observability data: metrics, logs, errors, spans and custom events. The {@link Observability}
 * plugin starts it when the {@code LDClient} is created; {@link #init} starts it without one.
 * <p>
 * Every method is safe to call from any thread, and before the plugin starts or after
 * {@link #shutdown()}, when it records nothing. None throws on a null argument: a null throwable,
 * message or metric records nothing, and null attributes count as none.
 *
 * <pre><code>
 *     LDObserve.recordLog("Export finished", Severity.INFO, Attributes.empty());
 *     LDObserve.recordError(exception, Attributes.empty());
 *     Span span = LDObserve.startSpan("load-report", Attributes.empty());
 *     try {
 *       // ...
 *     } finally {
 *       span.end();
 *     }
 * </code></pre>
 */
public final class LDObserve {
  private LDObserve() {}

  /**
   * Starts observability without an {@code LDClient}. With a client, add the {@link Observability}
   * plugin instead. Does nothing when {@link ObservabilityOptions#isEnabled()} is false, or when
   * observability is already running; a second credential is refused, since telemetry is filed under
   * one project.
   *
   * @param credential the mobile key or client-side ID of the LaunchDarkly environment
   * @param options the options
   */
  public static void init(String credential, ObservabilityOptions options) {
    Objects.requireNonNull(credential, "credential");
    Objects.requireNonNull(options, "options");
    if (options.isEnabled()) {
      ObservabilityRuntime.start(credential, options, null);
    }
  }

  // -- metrics --

  /**
   * Records a gauge: the latest value of something.
   *
   * @param metric the metric
   */
  public static void recordMetric(Metric metric) {
    ObservabilityService s = ObservabilityRuntime.current();
    if (s != null && metric != null) {
      s.recordMetric(metric);
    }
  }

  /**
   * Adds the metric's whole value to a counter.
   *
   * @param metric the metric
   */
  public static void recordCount(Metric metric) {
    ObservabilityService s = ObservabilityRuntime.current();
    if (s != null && metric != null) {
      s.recordCount(metric);
    }
  }

  /**
   * Adds one to a counter. The metric's value is ignored.
   *
   * @param metric the metric
   */
  public static void recordIncr(Metric metric) {
    ObservabilityService s = ObservabilityRuntime.current();
    if (s != null && metric != null) {
      s.recordIncr(metric);
    }
  }

  /**
   * Records a value in a histogram.
   *
   * @param metric the metric
   */
  public static void recordHistogram(Metric metric) {
    ObservabilityService s = ObservabilityRuntime.current();
    if (s != null && metric != null) {
      s.recordHistogram(metric);
    }
  }

  /**
   * Adds the metric's whole value, which may be negative, to an up/down counter.
   *
   * @param metric the metric
   */
  public static void recordUpDownCounter(Metric metric) {
    ObservabilityService s = ObservabilityRuntime.current();
    if (s != null && metric != null) {
      s.recordUpDownCounter(metric);
    }
  }

  // -- logs --

  /**
   * Records a log message, correlated with the current span if there is one.
   *
   * @param message the message
   * @param severity the severity; null means {@link Severity#INFO}
   * @param attributes the attributes
   */
  public static void recordLog(String message, Severity severity, Attributes attributes) {
    recordLog(message, severity, attributes, null);
  }

  /**
   * Records a log message correlated with a particular span.
   *
   * @param message the message
   * @param severity the severity; null means {@link Severity#INFO}
   * @param attributes the attributes
   * @param spanContext the span to correlate the record with, or null for the current span
   */
  public static void recordLog(String message, Severity severity, Attributes attributes, SpanContext spanContext) {
    ObservabilityService s = ObservabilityRuntime.current();
    if (s != null && message != null) {
      s.recordLog(message, severity == null ? Severity.INFO : severity, orEmpty(attributes), spanContext);
    }
  }

  /**
   * Records a log message whose attributes are plain values, converted as for {@link #track}.
   *
   * @param message the message
   * @param severity the severity; null means {@link Severity#INFO}
   * @param properties the attributes as plain values
   */
  public static void recordLog(String message, Severity severity, Map<String, ?> properties) {
    recordLog(message, severity, AttributeConversion.fromMap(properties), null);
  }

  // -- traces --

  /**
   * Records an error as a {@code highlight.error} span carrying the exception, a child of the current
   * span if there is one.
   *
   * @param error the error
   * @param attributes attributes for the error
   */
  public static void recordError(Throwable error, Attributes attributes) {
    ObservabilityService s = ObservabilityRuntime.current();
    if (s != null && error != null) {
      s.recordError(error, orEmpty(attributes));
    }
  }

  /**
   * Starts a span, a child of the current span if there is one. The caller must end it.
   *
   * @param name the span name
   * @param attributes the span's attributes
   * @return the span, or an invalid span that records nothing when observability is not running or
   *   spans are turned off
   */
  public static Span startSpan(String name, Attributes attributes) {
    ObservabilityService s = ObservabilityRuntime.current();
    if (s == null || name == null) {
      return Span.getInvalid();
    }
    return s.startSpan(name, orEmpty(attributes));
  }

  /**
   * Starts a span whose attributes are plain values, converted as for {@link #track}.
   *
   * @param name the span name
   * @param properties the attributes as plain values
   * @return the span; see {@link #startSpan(String, Attributes)}
   */
  public static Span startSpan(String name, Map<String, ?> properties) {
    return startSpan(name, AttributeConversion.fromMap(properties));
  }

  /**
   * Records a custom event as a {@code track} span, the same span {@code LDClient.track} produces.
   * Use it for events you do not send to LaunchDarkly as analytics. The span carries the context
   * keys of the last completed identify.
   * <p>
   * Properties are plain values: booleans and strings; integers, kept as longs; floating-point
   * numbers, as doubles; lists of one of those, as arrays; and nested maps and other lists,
   * flattened into dotted keys such as {@code user.id} and {@code items.0.price}. Anything else is
   * skipped.
   *
   * @param key the event key
   * @param properties the event's data, or null
   * @param metricValue a numeric value for the event, or null
   */
  public static void track(String key, Map<String, ?> properties, Double metricValue) {
    ObservabilityService s = ObservabilityRuntime.current();
    if (s != null && key != null) {
      s.track(key, metricValue, AttributeConversion.fromMap(properties), null);
    }
  }

  /**
   * Records a screen view as a {@code screen_view} span, for screens the plugin cannot see itself,
   * such as the tabs or cards inside one window; each window is a screen already. Showing the screen
   * that is already current records nothing. The span carries {@code event.previous_screen}, and
   * clicks that follow carry this screen's id and name.
   *
   * @param name the screen's name, {@code event.name}
   * @param screenClass the class behind the screen, or null
   * @param screenId a stable id for the screen, or null to tell screens apart by name
   * @param category a group the screen belongs to, or null
   * @param properties more attributes as plain values, converted as for {@link #track}; they cannot
   *   replace the {@code event.*} fields
   */
  public static void trackScreenView(String name, String screenClass, String screenId, String category,
      Map<String, ?> properties) {
    ObservabilityService s = ObservabilityRuntime.current();
    if (s != null && name != null) {
      s.trackScreenView(name, screenClass, screenId, category, AttributeConversion.fromMap(properties));
    }
  }

  // -- attributes --

  /**
   * Merges attributes into the global attributes, which every span, log record and metric recorded
   * afterwards carries. They never replace an attribute the span, record or metric has of its own,
   * and a span gets the values current when it starts. A key already present takes the new value.
   * The session keys {@code session.id} and {@code highlight.session_id} are reserved and ignored.
   * <p>
   * Kept in memory for the life of the process, across {@link #shutdown()}, and safe to call before
   * observability starts.
   *
   * @param attributes the attributes to add or replace
   */
  public static void setAttributes(Attributes attributes) {
    Set<String> dropped = GlobalAttributes.set(attributes);
    if (!dropped.isEmpty()) {
      ObservabilityService s = ObservabilityRuntime.current();
      if (s != null) {
        s.logReservedAttributes(dropped);
      }
    }
  }

  /**
   * Merges plain values into the global attributes, converted as for {@link #track}.
   *
   * @param properties the attributes as plain values
   */
  public static void setAttributes(Map<String, ?> properties) {
    setAttributes(AttributeConversion.fromMap(properties));
  }

  /**
   * Removes the global attributes with these key names. Names that are not set are ignored.
   *
   * @param keys the key names
   */
  public static void removeAttributes(Collection<String> keys) {
    GlobalAttributes.remove(keys);
  }

  // -- lifecycle --

  /**
   * The current session id, which every signal carries as {@code session.id}. A session lasts at most
   * four hours; the next signal after that starts a new one.
   *
   * @return the session id, or null when observability is not running
   */
  public static String getSessionId() {
    ObservabilityService s = ObservabilityRuntime.current();
    return s == null ? null : s.getSessionId();
  }

  /**
   * The plugin's OpenTelemetry, for instrumentation that should export to LaunchDarkly through the
   * same resource, session and sampling rules. It is not registered as {@code GlobalOpenTelemetry}.
   *
   * @return the plugin's OpenTelemetry, or a no-op one when observability is not running. Take it
   *   after the plugin starts; one taken before stays a no-op.
   */
  public static OpenTelemetry getOpenTelemetry() {
    ObservabilityService s = ObservabilityRuntime.current();
    return s == null ? OpenTelemetry.noop() : s.getOpenTelemetry();
  }

  /**
   * Starts exporting everything recorded so far. Returns without waiting, so it is safe to call from
   * the UI thread.
   */
  public static void flush() {
    ObservabilityService s = ObservabilityRuntime.current();
    if (s != null) {
      s.flush();
    }
  }

  /**
   * Exports everything recorded so far, waiting up to five seconds, and stops. Later calls record
   * nothing. The plugin's JVM shutdown hook does this on exit; call it yourself when you turned the
   * hook off with {@link ObservabilityOptions.Builder#shutdownHook(boolean)}, or to stop earlier.
   */
  public static void shutdown() {
    ObservabilityRuntime.shutdown();
  }

  private static Attributes orEmpty(Attributes attributes) {
    return attributes == null ? Attributes.empty() : attributes;
  }
}
