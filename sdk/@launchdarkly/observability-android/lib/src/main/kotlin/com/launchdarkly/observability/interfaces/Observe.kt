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
    fun recordLog(message: String, severity: Severity, attributes: Attributes, spanContext: SpanContext?)
}

fun LogsApi.recordLog(message: String, severity: Severity, attributes: Attributes) {
    recordLog(message, severity, attributes, null)
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
    fun startSpan(name: String, attributes: Attributes): Span
}

/**
 * Interface for observability operations in the LaunchDarkly Android SDK.
 * Provides methods for recording various types of information.
 */
interface Observe : MetricsApi, LogsApi, TracesApi {
    /**
     * Flushes all pending telemetry data (traces, logs, metrics).
     * @return true if all flush operations succeeded, false otherwise
     */
    fun flush(): Boolean
}
