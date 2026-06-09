package com.launchdarkly.observability.interfaces

import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.logs.Severity
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanContext

interface MetricsApi {
    /**
     * Record a metric value.
     * @param metric The metric to record
     */
    fun recordMetric(metric: Metric)

    /**
     * Record a count metric.
     * @param metric The count metric to record
     */
    fun recordCount(metric: Metric)

    /**
     * Record an increment metric.
     * @param metric The increment metric to record
     */
    fun recordIncr(metric: Metric)

    /**
     * Record a histogram metric.
     * @param metric The histogram metric to record
     */
    fun recordHistogram(metric: Metric)

    /**
     * Record an up/down counter metric.
     * @param metric The up/down counter metric to record
     */
    fun recordUpDownCounter(metric: Metric)
}

interface LogsApi {
    /**
     * Record a log message with optional span context for trace-log correlation.
     * @param message The log message to record
     * @param severity The severity of the log message
     * @param attributes The attributes to record with the log message
     * @param spanContext Optional span context for trace-log correlation
     */
    fun recordLog(message: String, severity: Severity, attributes: Attributes = Attributes.empty(), spanContext: SpanContext? = null)
}

interface TracesApi {
    /**
     * Record an error.
     * @param error The error to record
     * @param attributes The attributes to record with the error
     */
    fun recordError(error: Error, attributes: Attributes)

    /**
     * Start a span.
     * @param name The name of the span
     * @param attributes The attributes to record with the span
     */
    fun startSpan(name: String, attributes: Attributes = Attributes.empty()): Span
}

/**
 * Interface for observability operations in the LaunchDarkly Android SDK.
 * Provides methods for recording various types of information.
 */
interface Observe : MetricsApi, LogsApi, TracesApi {
    /**
     * Requests a flush of all pending telemetry data (traces, logs, metrics).
     *
     * This call is non-blocking: it signals the internal batch worker to drain the queue and
     * returns immediately. Export happens asynchronously on background dispatchers.
     */
    fun flush()

    /**
     * Record a custom track event as a `track` span.
     *
     * Mirrors `LDClient.track(key, data, metricValue)` so the same call shape works
     * whether the event is recorded through the LaunchDarkly client (via the
     * `afterTrack` hook) or directly through this API. `data` is a plain map so
     * callers need not depend on `LDValue`.
     * @param key The key for the event.
     * @param data The data associated with the event, if any. Object members are
     *   attached as span attributes.
     * @param metricValue A numeric value used by LaunchDarkly experimentation for
     *   numeric custom metrics, if any.
     */
    fun track(key: String, data: Map<String, Any?>? = null, metricValue: Double? = null)

    /**
     * Record a screen view as a `screen_view` span, following the analytics taxonomy `event.*`
     * namespace. Use this for screens that are not backed by a distinct Android `Activity` (e.g.
     * Fragments or Compose destinations); activities are captured automatically when
     * [com.launchdarkly.observability.api.ObservabilityOptions.Instrumentations.screens] is enabled.
     *
     * @param name Human-readable screen name (maps to `event.name`).
     * @param screenClass Optional class backing the screen (maps to `event.screen_class`).
     * @param screenId Optional stable identifier (maps to `event.screen_id`).
     * @param category Optional screen group (maps to `event.category`).
     */
    fun trackScreenView(
        name: String,
        screenClass: String? = null,
        screenId: String? = null,
        category: String? = null
    )
}
