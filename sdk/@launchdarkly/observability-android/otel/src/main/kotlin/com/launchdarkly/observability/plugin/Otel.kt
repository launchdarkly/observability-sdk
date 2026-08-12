package com.launchdarkly.observability.plugin

import android.app.Application
import com.launchdarkly.observability.api.ObservabilityOptions
import com.launchdarkly.observability.sdk.LDObserve

/**
 * The OpenTelemetry-only LaunchDarkly plugin.
 *
 * Records exactly what the app asks it to through [LDObserve] — logs, spans, metrics, errors and
 * `track` events — and exports them over OTLP. It installs nothing into the host app: no crash
 * handler, no window-callback wrapping or tap capture, no activity lifecycle screen detection, no
 * launch timing, and no classpath scanning for OpenTelemetry Android instrumentation. That makes it
 * safe to run alongside another observability SDK, which
 * [com.launchdarkly.observability.plugin.Observability] is not, since the two would compete for the
 * same hooks.
 *
 * Flag evaluation, identify and `track` hooks still work, and every signal is stamped with the
 * current session, including rotation after a background timeout.
 *
 * ```
 * val ldConfig = LDConfig.Builder(LDConfig.Builder.AutoEnvAttributes.Enabled)
 *     .mobileKey(LAUNCHDARKLY_MOBILE_KEY)
 *     .plugins(
 *         Components.plugins().setPlugins(
 *             listOf(
 *                 Otel(this@BaseApplication, LAUNCHDARKLY_MOBILE_KEY)
 *             )
 *         )
 *     )
 *     .build()
 * ```
 *
 * Use `Observability` from `com.launchdarkly:launchdarkly-observability-android` instead when you
 * want automatic instrumentation, crash reporting, or Session Replay.
 *
 * @param application The application instance.
 * @param mobileKey The primary mobile key used in LDConfig.
 * @param options Pipeline configuration. The `instrumentations` section is ignored: this plugin
 *   installs no instrumentation.
 * @param customSessionId Optional session id to adopt instead of generating one.
 */
class Otel(
    application: Application,
    mobileKey: String,
    options: ObservabilityOptions = ObservabilityOptions(),
    customSessionId: String? = null,
) : ObservabilityPlugin(
    application = application,
    mobileKey = mobileKey,
    options = options,
    customSessionId = customSessionId,
    pluginName = PLUGIN_NAME,
    distroName = DISTRO_NAME,
) {
    companion object {
        const val PLUGIN_NAME = "@launchdarkly/otel-android"
        private const val DISTRO_NAME = "launchdarkly-otel-android"
    }
}
