package com.launchdarkly.observability.interfaces

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
}
