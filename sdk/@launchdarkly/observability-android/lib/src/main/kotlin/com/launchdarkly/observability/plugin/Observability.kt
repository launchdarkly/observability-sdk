package com.launchdarkly.observability.plugin

import android.app.Application
import com.launchdarkly.observability.api.ObservabilityOptions
import com.launchdarkly.observability.client.DefaultInstrumentation
import com.launchdarkly.observability.client.ObservabilityInstrumenting
import com.launchdarkly.observability.sdk.LDObserve

/**
 * This Observability class is a plugin implementation for recording observability data such as metrics, logs, errors, and traces.
 * Provide the plugin to the LaunchDarkly Android Client SDK to enable observability.
 *
 * ```
 * val ldConfig = LDConfig.Builder(LDConfig.Builder.AutoEnvAttributes.Enabled)
 *     .mobileKey(LAUNCHDARKLY_MOBILE_KEY)
 *     .plugins(
 *         Components.plugins().setPlugins(
 *             listOf(
 *                 Observability(this@BaseApplication)
 *             )
 *         )
 *     )
 *     .build()
 * ```
 *
 * Later after initialization you can use [LDObserve] to record observability data.
 *
 * ```
 * LDObserve.recordMetric(metric)
 * LDObserve.recordLog(message, severity, attributes)
 * LDObserve.recordError(error, attributes)
 * LDObserve.startSpan(name, attributes)
 * ```
 *
 * This plugin installs automatic instrumentation — crash reporting, tap capture, screen detection,
 * launch timing — which claims process-wide hooks. Use [Otel] instead when another observability
 * SDK already owns those hooks, or when you only want the manual recording APIs.
 *
 * @param application The application instance.
 * @param options The options for the plugin.
 * @param mobileKey The primary mobile key used in LDConfig.
 * @param customSessionId Optional session id to adopt instead of generating one. Lets the native
 *   instance share a single `session.id` with another LaunchDarkly SDK on the device (e.g. the
 *   JavaScript SDK in a React Native app). When null, a session id is generated automatically.
 */
class Observability(
    application: Application,
    mobileKey: String,
    private val options: ObservabilityOptions = ObservabilityOptions(), // new instance has reasonable defaults
    customSessionId: String? = null,
) : ObservabilityPlugin(
    application = application,
    mobileKey = mobileKey,
    options = options,
    customSessionId = customSessionId,
    pluginName = PLUGIN_NAME,
    distroName = DISTRO_NAME,
) {
    override fun createInstrumentation(sdkKey: String): ObservabilityInstrumenting =
        DefaultInstrumentation(sdkKey, options, logger)

    companion object {
        const val PLUGIN_NAME = "@launchdarkly/observability-android"
        private const val DISTRO_NAME = "launchdarkly-observability-android"
    }
}
