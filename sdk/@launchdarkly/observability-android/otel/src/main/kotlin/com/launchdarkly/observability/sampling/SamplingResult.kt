package com.launchdarkly.observability.sampling

import io.opentelemetry.api.common.Attributes

data class SamplingResult(
    /**
     * Whether the span should be sampled.
     */
    val sample: Boolean,

    /**
     * The attributes to add to the span.
     */
    val attributes: Attributes? = null
)
