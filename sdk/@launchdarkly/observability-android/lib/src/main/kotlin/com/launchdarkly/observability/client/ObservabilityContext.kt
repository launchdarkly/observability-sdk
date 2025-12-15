package com.launchdarkly.observability.client

import android.app.Application
import com.launchdarkly.logging.LDLogger
import com.launchdarkly.observability.api.Options

/**
 * Shared information between plugins.
 */
data class ObservabilityContext(
    val sdkKey: String,
    val options: Options,
    val application: Application,
    val logger: LDLogger,
)
