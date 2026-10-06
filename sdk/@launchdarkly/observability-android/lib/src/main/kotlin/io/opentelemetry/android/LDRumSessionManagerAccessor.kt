package io.opentelemetry.android

import io.opentelemetry.android.session.SessionManager

/**
 * Bridges to [OpenTelemetryRumBuilder.setSessionManager], which is package-private in
 * OpenTelemetry Android. Declaring this object in the same `io.opentelemetry.android` package lets
 * the LaunchDarkly SDK supply its own [SessionManager] (see
 * [com.launchdarkly.observability.client.LDSessionManager]) so it backs the RUM SDK's `session.id`
 * span/log appenders, rather than relying on the default manager captured via an instrumentation.
 */
internal object LDRumSessionManagerAccessor {
    fun setSessionManager(
        builder: OpenTelemetryRumBuilder,
        sessionManager: SessionManager,
    ): OpenTelemetryRumBuilder = builder.setSessionManager(sessionManager)
}
