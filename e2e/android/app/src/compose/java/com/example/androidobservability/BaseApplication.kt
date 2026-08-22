package com.example.androidobservability

import android.app.Application
import android.util.Log
import android.widget.ImageView
import com.launchdarkly.observability.api.ObservabilityOptions
import com.launchdarkly.observability.replay.PrivacyProfile
import com.launchdarkly.observability.replay.ReplayOptions
import com.launchdarkly.observability.replay.view
import com.launchdarkly.observability.context.LDObserveContext
import com.launchdarkly.observability.sdk.LDObserve
import com.launchdarkly.observability.sdk.LDReplay
import com.launchdarkly.sdk.ContextKind
import com.launchdarkly.sdk.LDContext
import com.launchdarkly.sdk.android.FeatureFlagChangeListener
import com.launchdarkly.sdk.android.LDClient
import com.launchdarkly.sdk.android.LDConfig
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import kotlin.time.Duration.Companion.minutes

open class BaseApplication : Application() {

    companion object {
        const val LAUNCHDARKLY_MOBILE_KEY = BuildConfig.LAUNCHDARKLY_MOBILE_KEY
    }

    /**
     * Which setup [onCreate] runs: standalone observability ([initIndependently]) or observability
     * attached to an initialized [LDClient] ([initWithFlagClient]). Flip it to exercise the app
     * without the flagging SDK, which also hides the screens' LDClient-driven controls.
     */
    var isIndependent = false

    // A minimal setup with all
    // automatic instrumentation disabled, for testing the `Instrumentations.disabled()` path.
//    var observabilityOptions = ObservabilityOptions(
//        enabled = true,
//        serviceName = "observability-android-test-app",
//        instrumentations = ObservabilityOptions.Instrumentations.disabled(),
//    )

    // Current configuration, commented out for testing.
    var observabilityOptions = ObservabilityOptions(
        resourceAttributes = Attributes.of(
            AttributeKey.stringKey("resourceAttributes"), "BaseApplication"
        ),
        // Report the app's own version rather than the SDK's, which is what the
        // default would be. Symbolication matches an uploaded mapping.txt to a
        // build by this version, so it has to name a build of the app.
        serviceVersion = BuildConfig.VERSION_NAME,
        debug = true,
        otlpEndpoint = BuildConfig.OTLP_ENDPOINT,
        backendUrl = BuildConfig.BACKEND_URL,
        sessionBackgroundTimeout = 3.minutes,
        tracesApi = ObservabilityOptions.TracesApi.enabled(),
        metricsApi = ObservabilityOptions.MetricsApi.enabled(),
        instrumentations = ObservabilityOptions.Instrumentations(
            crashReporting = true, launchTime = true
        ),
        analytics = ObservabilityOptions.Analytics(
            taps = true, screenViews = true, trackEvents = true
        ),
    )

    var testUrl: String? = null

    // example on creating OBS/SR with flagging sdk
    open fun initWithFlagClient() {
        val effectiveOptions = testUrl?.let {
            observabilityOptions.copy(backendUrl = it, otlpEndpoint = it)
        } ?: observabilityOptions

        // Set LAUNCHDARKLY_MOBILE_KEY to your LaunchDarkly mobile key found on the LaunchDarkly
        // dashboard in the start guide.
        // If you want to disable the Auto EnvironmentAttributes functionality.
        // Use AutoEnvAttributes.Disabled as the argument to the Builder
        val ldConfig = LDConfig.Builder(LDConfig.Builder.AutoEnvAttributes.Enabled)
            .mobileKey(LAUNCHDARKLY_MOBILE_KEY)
            .build()

        // Set up the context properties. This context should appear on your LaunchDarkly context
        // dashboard soon after you run the demo.
        val context = LDContext.builder(ContextKind.DEFAULT, "example-user-key")
            .anonymous(true)
            .build()

        val ldClient = LDClient.init(this@BaseApplication, ldConfig, context, 0)

        LDObserve.init(
            application = this@BaseApplication,
            ldClient = ldClient,
            ldContext = LDObserveContext.builder(LDObserveContext.DEFAULT_KIND, "example-user-key")
                .anonymous(true)
                .build(),
            observability = effectiveOptions,
            replay = ReplayOptions(
                enabled = false,
                privacyProfile = PrivacyProfile(
                    maskText = false,
                    maskWebViews = true,
                    maskViews = listOf(
                        view(ImageView::class.java),
                    ),
                    maskXMLViewIds = listOf("smoothieTitle")
                ),
                sampleRate = 1.0,
                frameRate = 1.0
            )
        )

        if (testUrl == null) {
            // intervenes in E2E tests by trigger spans
            flagEvaluation()
        }

        LDReplay.start()
    }

    // example on creating OBS/SR without flagging
    open fun initIndependently() {
        val effectiveOptions = testUrl?.let {
            observabilityOptions.copy(backendUrl = it, otlpEndpoint = it)
        } ?: observabilityOptions

        val context = LDObserveContext.builder(LDObserveContext.DEFAULT_KIND, "example-user-key")
            .anonymous(true)
            .build()

        Thread {
            LDObserve.init(
                application = this@BaseApplication,
                mobileKey = LAUNCHDARKLY_MOBILE_KEY,
                ldContext = context,
                observability = effectiveOptions,
                replay = ReplayOptions(
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

       }.start()

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
        if (isIndependent) {
            initIndependently()
        } else {
            initWithFlagClient()
        }
    }
}
