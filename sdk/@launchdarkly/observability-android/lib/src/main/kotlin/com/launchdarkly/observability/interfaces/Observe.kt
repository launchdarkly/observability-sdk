package com.launchdarkly.observability.interfaces

import com.launchdarkly.observability.bridge.toOtelAttributes
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

    /**
     * Record a log whose attributes are supplied as a plain map.
     *
     * Prefer this over the [Attributes] overload for everyday use: pass native
     * values (String, Boolean, Int, Long, Double, lists, nested maps) and they are
     * converted to OTel attributes with the same rules as a `track` event's
     * `properties`. The [Attributes] overload remains available when you need
     * precise OpenTelemetry typing. A distinct parameter name keeps the two
     * overloads unambiguous.
     */
    fun recordLog(message: String, severity: Severity, properties: Map<String, Any?>, spanContext: SpanContext? = null) {
        recordLog(message, severity, properties.toOtelAttributes(), spanContext)
    }
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

    /**
     * Start a span whose attributes are supplied as a plain map.
     *
     * Prefer this over the [Attributes] overload for everyday use: pass native
     * values and they are converted to OTel attributes with the same rules as a
     * `track` event's `properties`. The [Attributes] overload remains available
     * when you need precise OpenTelemetry typing.
     */
    fun startSpan(name: String, properties: Map<String, Any?>): Span {
        return startSpan(name, properties.toOtelAttributes())
    }
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
     * Mirrors `LDClient.track(...)` so the same call shape works whether the event
     * is recorded through the LaunchDarkly client (via the `afterTrack` hook) or
     * directly through this API. The payload is passed as `properties`, a plain
     * map, so callers need not depend on `LDValue` — matching the `properties`
     * overloads of `recordLog`/`startSpan`.
     * @param key The key for the event.
     * @param properties The data associated with the event, if any. Object members
     *   are attached as span attributes.
     * @param metricValue A numeric value used by LaunchDarkly experimentation for
     *   numeric custom metrics, if any.
     */
    fun track(key: String, properties: Map<String, Any?>? = null, metricValue: Double? = null)

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
     * @param properties Optional custom attributes, supplied as a plain map (same
     *   conversion rules as a `track` event's `properties`). They are attached at
     *   lower precedence than the reserved `event.*` fields, so they can never
     *   clobber the taxonomy.
     */
    fun trackScreenView(
        name: String,
        screenClass: String? = null,
        screenId: String? = null,
        category: String? = null,
        properties: Map<String, Any?>? = null
    )

    /**
     * Manually record a `click` event as a `click` span, following the analytics taxonomy `event.*`
     * namespace. Use this to reproduce the `click` event for interactions that automatic tap
     * capture cannot observe. Emitted through the same `analytics.taps` gate as automatic click
     * spans.
     *
     * @param id Stable element identifier (maps to `event.id`).
     * @param tag Element tag/class (maps to `event.tag`), e.g. `Button`.
     * @param text Visible label/text of the element (maps to `event.text`).
     * @param screenId Stable screen id (maps to `event.screen_id`). When `null`, the current
     *   tracked screen id is used so the click correlates with the active `screen_view`.
     * @param x Tap x coordinate in screen pixels (maps to `event.x`).
     * @param y Tap y coordinate in screen pixels (maps to `event.y`).
     * @param properties Optional custom attributes, supplied as a plain map (same conversion rules
     *   as a `track` event's `properties`). They are attached at lower precedence than the reserved
     *   `event.*` fields, so they can never clobber the taxonomy.
     */
    fun trackClick(
        id: String? = null,
        tag: String? = null,
        text: String? = null,
        screenId: String? = null,
        x: Int? = null,
        y: Int? = null,
        properties: Map<String, Any?>? = null
    )
}
