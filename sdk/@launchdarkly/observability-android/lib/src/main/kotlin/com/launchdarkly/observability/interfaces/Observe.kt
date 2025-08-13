package com.launchdarkly.observability.interfaces

import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.logs.Severity
import io.opentelemetry.api.trace.Span

/**
 * Interface for observability operations in the LaunchDarkly Android SDK.
 * Provides methods for recording various types of metrics.
 */
interface Observe {
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

    /**
     * Record an error.
     * @param error The error to record
     * @param attributes The attributes to record with the error
     * @param options The options to record with the error
     */
    fun recordError(error: Error, attributes: Attributes)

    /**
     * Record a log message.
     * @param message The log message to record
     * @param severity The severity of the log message
     * @param attributes The attributes to record with the log message
     */
    fun recordLog(message: String, severity: Severity, attributes: Attributes)

    /**
     * Start a span.
     * @param name The name of the span
     * @param attributes The attributes to record with the span
     */
    fun startSpan(name: String, attributes: Attributes): Span
}
