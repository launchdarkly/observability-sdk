package com.launchdarkly.observability.client

import android.app.Application
import com.launchdarkly.observability.api.ObservabilityOptions
import com.launchdarkly.observability.client.screen.ScreenViewEvent
import com.launchdarkly.observability.context.ObserveLogger
import io.opentelemetry.android.session.SessionManager
import io.opentelemetry.api.common.Attributes
import kotlinx.coroutines.flow.SharedFlow

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
    /**
     * The single touch-capture hook owned by Observability. Session Replay consumes its
     * [UserInteractionManager.touchFlow] instead of intercepting windows itself.
     */
    var userInteractionManager: UserInteractionManager? = null,
    /**
     * Ordered stream of recorded screen views (first screen and every change), owned by
     * Observability. Session Replay consumes it to emit `Navigate` events.
     */
    var screenViewFlow: SharedFlow<ScreenViewEvent>? = null,
)
