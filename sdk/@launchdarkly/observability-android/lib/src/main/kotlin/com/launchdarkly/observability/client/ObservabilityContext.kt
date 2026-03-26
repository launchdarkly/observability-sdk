package com.launchdarkly.observability.client

import android.app.Application
import com.launchdarkly.logging.LDLogger
import com.launchdarkly.observability.api.ObservabilityOptions
import io.opentelemetry.android.session.SessionManager

/**
 * Shared information between plugins.
 */
data class ObservabilityContext(
    val sdkKey: String,
    val options: ObservabilityOptions,
    val application: Application,
    val logger: LDLogger,
    var sessionManager: SessionManager? = null,
)
