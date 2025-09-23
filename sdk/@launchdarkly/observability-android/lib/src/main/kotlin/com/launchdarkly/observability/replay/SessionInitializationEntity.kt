package com.launchdarkly.observability.replay

import com.launchdarkly.observability.sampling.SamplingConfig

/**
 * Entity class for the initialize session response
 */
data class SessionInitializationEntity(
    val secureId: String?,
    val projectId: String?,
    val sampling: SamplingConfig?
)
