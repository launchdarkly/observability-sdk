package com.launchdarkly.observability.client

import android.app.Application
import com.launchdarkly.observability.api.ObservabilityOptions
import com.launchdarkly.observability.context.ObserveLogger
import io.opentelemetry.android.session.SessionManager
import io.opentelemetry.api.common.Attributes

/**
 * Shared information between plugins.
 */
data class ObservabilityContext(
    val sdkKey: String,
    val options: ObservabilityOptions,
    val application: Application,
    val logger: ObserveLogger,
    var sessionManager: SessionManager? = null,
    var resourceAttributes: Attributes = Attributes.empty(),
)
