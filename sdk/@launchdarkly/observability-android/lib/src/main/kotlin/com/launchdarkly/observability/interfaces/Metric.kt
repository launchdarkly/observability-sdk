package com.launchdarkly.observability.interfaces

import io.opentelemetry.api.common.Attributes

data class Metric(
    val name: String,
    val value: Double,
    val attributes: Attributes = Attributes.empty(),
    val timestamp: Long? = null,
)
