package com.example.androidobservability

import android.app.Application
import android.widget.ImageView
import com.launchdarkly.observability.api.ObservabilityOptions
import com.launchdarkly.observability.client.TelemetryInspector
import com.launchdarkly.observability.plugin.Observability
import com.launchdarkly.observability.replay.PrivacyProfile
import com.launchdarkly.observability.replay.ReplayOptions
import com.launchdarkly.observability.replay.plugin.SessionReplay
import com.launchdarkly.observability.replay.view
import com.launchdarkly.sdk.ContextKind
import com.launchdarkly.sdk.LDContext
import com.launchdarkly.sdk.android.Components
import com.launchdarkly.sdk.android.LDAndroidLogging
import com.launchdarkly.sdk.android.LDClient
import com.launchdarkly.sdk.android.LDConfig
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import org.mozilla.geckoview.GeckoRuntime

open class BaseApplication : Application() {

    companion object {
        // TODO O11Y-376: Update this credential to be driven by env variable or gradle property
        // Set LAUNCHDARKLY_MOBILE_KEY to your LaunchDarkly SDK mobile key.
        const val LAUNCHDARKLY_MOBILE_KEY = "MOBILE_KEY_GOES_HERE"
    }

    var observabilityOptions = ObservabilityOptions(
        resourceAttributes = Attributes.of(
            AttributeKey.stringKey("example"), "value"
        ),
        debug = true,
        tracesApi = ObservabilityOptions.TracesApi.enabled(),
        metricsApi = ObservabilityOptions.MetricsApi.enabled(),
        instrumentations = ObservabilityOptions.Instrumentations(
            crashReporting = true, launchTime = true, activityLifecycle = true
        ),
        logAdapter = LDAndroidLogging.adapter(),
    )

    var telemetryInspector: TelemetryInspector? = null
    var testUrl: String? = null

    open fun realInit() {
        val observabilityPlugin = Observability(
            application = this@BaseApplication,
            mobileKey = LAUNCHDARKLY_MOBILE_KEY,
            options = testUrl?.let { observabilityOptions.copy(backendUrl = it, otlpEndpoint = it) } ?: observabilityOptions
        )

        val sessionReplayPlugin = SessionReplay(
            options = ReplayOptions(
                privacyProfile = PrivacyProfile(
                    maskText = false,
                    maskWebViews = true,
                    maskViews = listOf(
                        view(ImageView::class.java),
                    ),
                    maskXMLViewIds = listOf("smoothieTitle")
                )
            )
        )

        // Set LAUNCHDARKLY_MOBILE_KEY to your LaunchDarkly mobile key found on the LaunchDarkly
        // dashboard in the start guide.
        // If you want to disable the Auto EnvironmentAttributes functionality.
        // Use AutoEnvAttributes.Disabled as the argument to the Builder
        val ldConfig = LDConfig.Builder(LDConfig.Builder.AutoEnvAttributes.Enabled)
            .mobileKey(LAUNCHDARKLY_MOBILE_KEY)
            .plugins(
                Components.plugins().setPlugins(
                    listOf(
                        observabilityPlugin,
                        sessionReplayPlugin
                    )
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
