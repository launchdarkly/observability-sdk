package com.launchdarkly.observability.interfaces

import io.opentelemetry.api.common.Attributes

/**
 * A metric is a value that can be recorded in the LaunchDarkly observability system.
 * 
 * @param name The name of the metric.
 * @param value The value of the metric.
 * @param attributes The attributes of the metric.
 * @param timestamp The timestamp of the metric.
 */
data class Metric(
    val name: String,
    val value: Double,
    val attributes: Attributes = Attributes.empty(),
    val timestamp: Long? = null,
)
