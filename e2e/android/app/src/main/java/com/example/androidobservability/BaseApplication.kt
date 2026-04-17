package com.example.androidobservability

import android.app.Application
import android.util.Log
import android.widget.ImageView
import com.launchdarkly.observability.api.ObservabilityOptions
import com.launchdarkly.observability.plugin.Observability
import com.launchdarkly.observability.replay.PrivacyProfile
import com.launchdarkly.observability.replay.ReplayOptions
import com.launchdarkly.observability.replay.plugin.SessionReplay
import com.launchdarkly.observability.replay.view
import com.launchdarkly.observability.context.LDObserveContext
import com.launchdarkly.observability.sdk.LDObserve
import com.launchdarkly.observability.sdk.LDReplay
import com.launchdarkly.sdk.ContextKind
import com.launchdarkly.sdk.LDContext
import com.launchdarkly.sdk.android.Components
import com.launchdarkly.sdk.android.FeatureFlagChangeListener
import com.launchdarkly.sdk.android.LDClient
import com.launchdarkly.sdk.android.LDConfig
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes

open class BaseApplication : Application() {

    companion object {
        const val LAUNCHDARKLY_MOBILE_KEY = BuildConfig.LAUNCHDARKLY_MOBILE_KEY
    }

    var observabilityOptions = ObservabilityOptions(
        resourceAttributes = Attributes.of(
            AttributeKey.stringKey("resourceAttributes"), "BaseApplication"
        ),
        debug = true,
        otlpEndpoint = BuildConfig.OTLP_ENDPOINT,
        backendUrl = BuildConfig.BACKEND_URL,
        tracesApi = ObservabilityOptions.TracesApi.enabled(),
        metricsApi = ObservabilityOptions.MetricsApi.enabled(),
        instrumentations = ObservabilityOptions.Instrumentations(
            crashReporting = true, launchTime = true, activityLifecycle = true
        ),
    )

    val sessionReplayPlugin = SessionReplay(
        options = ReplayOptions(
            enabled = false,
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

    var testUrl: String? = null

    open fun realInit2() {
        val observabilityPlugin = Observability(
            application = this@BaseApplication,
            mobileKey = LAUNCHDARKLY_MOBILE_KEY,
            options = testUrl?.let { observabilityOptions.copy(backendUrl = it, otlpEndpoint = it) } ?: observabilityOptions
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

        LDClient.init(this@BaseApplication, ldConfig, context, 1)

        if (testUrl == null) {
            // intervenes in E2E tests by trigger spans
            flagEvaluation()
        }

        LDReplay.start()
    }

    open fun realInit() {
        val effectiveOptions = testUrl?.let {
            observabilityOptions.copy(backendUrl = it, otlpEndpoint = it)
        } ?: observabilityOptions

        val context = LDObserveContext.builder(LDObserveContext.DEFAULT_KIND, "example-user-key")
            .anonymous(true)
            .build()

        LDObserve.init(
            application = this@BaseApplication,
            mobileKey = LAUNCHDARKLY_MOBILE_KEY,
            ldContext = context,
            options = effectiveOptions,
            replayOptions = ReplayOptions(
                enabled = false,
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

        LDReplay.start()
    }

    fun flagEvaluation() {
        val flagKey = "feature1"
        val value = LDClient.get().boolVariation(flagKey, false)
        Log.i("flag", "sync ${flagKey} value= ${value}")
        val listener = FeatureFlagChangeListener {
            val newValue = LDClient.get().boolVariation(flagKey, false)
            Log.i("flag", "listened ${flagKey} value= ${newValue}")
        }
        LDClient.get().registerFeatureFlagListener(flagKey, listener)
    }

    override fun onCreate() {
        super.onCreate()
        realInit()
    }
}
