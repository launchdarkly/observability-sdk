package com.example.androidobservability

import PrivacyProfile.Companion.textInputMatcher
import android.app.Application
import com.launchdarkly.observability.api.Options
import com.launchdarkly.observability.client.TelemetryInspector
import com.launchdarkly.sdk.ContextKind
import com.launchdarkly.sdk.LDContext
import com.launchdarkly.sdk.android.Components
import com.launchdarkly.sdk.android.LDClient
import com.launchdarkly.sdk.android.LDConfig
import com.launchdarkly.observability.plugin.Observability
import com.launchdarkly.observability.replay.ReplayInstrumentation
import com.launchdarkly.observability.replay.ReplayOptions
import com.launchdarkly.sdk.android.LDAndroidLogging
import com.launchdarkly.sdk.android.integrations.Plugin
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import java.util.Collections

open class BaseApplication : Application() {

    companion object {
        // TODO O11Y-376: Update this credential to be driven by env variable or gradle property
        // Set LAUNCHDARKLY_MOBILE_KEY to your LaunchDarkly SDK mobile key.
        const val LAUNCHDARKLY_MOBILE_KEY = "mob-a4328fa9-51e7-4a3d-bb0e-d5ce47464fc6"
    }

    var pluginOptions = Options(
        resourceAttributes = Attributes.of(
            AttributeKey.stringKey("example"), "value"
        ),
        debug = true,
        logAdapter = LDAndroidLogging.adapter(),
        // TODO: consider these being factories so that the obs plugin can pass instantiation data, log adapter
        instrumentations = listOf(
            ReplayInstrumentation(
                options = ReplayOptions(
                    privacyProfile = PrivacyProfile.strict()
                )
            )
        ),
    )

    var telemetryInspector: TelemetryInspector? = null
    var testUrl: String? = null

    open fun realInit() {
        val observabilityPlugin = Observability(
            application = this@BaseApplication,
            options = testUrl?.let { pluginOptions.copy(backendUrl = it, otlpEndpoint = it) } ?: pluginOptions
        )

        // Set LAUNCHDARKLY_MOBILE_KEY to your LaunchDarkly mobile key found on the LaunchDarkly
        // dashboard in the start guide.
        // If you want to disable the Auto EnvironmentAttributes functionality.
        // Use AutoEnvAttributes.Disabled as the argument to the Builder
        val ldConfig = LDConfig.Builder(LDConfig.Builder.AutoEnvAttributes.Enabled)
            .mobileKey(LAUNCHDARKLY_MOBILE_KEY)
            .plugins(
                Components.plugins().setPlugins(
                    Collections.singletonList<Plugin>(observabilityPlugin)
                )
            )
            .build()

        // Set up the context properties. This context should appear on your LaunchDarkly context
        // dashboard soon after you run the demo.
        val context = LDContext.builder(ContextKind.DEFAULT, "example-user-key")
            .anonymous(true)
            .build()

        LDClient.init(this@BaseApplication, ldConfig, context)
        telemetryInspector = observabilityPlugin.getTelemetryInspector()
    }

    override fun onCreate() {
        super.onCreate()
        realInit()
    }
}
