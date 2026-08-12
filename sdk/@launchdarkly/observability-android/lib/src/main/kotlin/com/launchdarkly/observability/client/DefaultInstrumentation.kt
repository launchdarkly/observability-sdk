package com.launchdarkly.observability.client

import android.app.Application
import com.launchdarkly.observability.api.ObservabilityOptions
import com.launchdarkly.observability.client.screen.ScreenView
import com.launchdarkly.observability.client.screen.ScreenViewManager
import com.launchdarkly.observability.context.ObserveLogger
import com.launchdarkly.observability.coroutines.DispatcherProviderHolder
import com.launchdarkly.observability.network.GraphQLClient
import com.launchdarkly.observability.network.SamplingApiService
import com.launchdarkly.observability.sampling.CustomSampler
import com.launchdarkly.observability.sampling.ExportSampler
import com.launchdarkly.observability.sampling.SamplingConfig
import io.opentelemetry.android.LDRumSessionManagerAccessor
import io.opentelemetry.android.OpenTelemetryRum
import io.opentelemetry.android.OpenTelemetryRumBuilder
import io.opentelemetry.android.config.OtelRumConfig
import io.opentelemetry.sdk.OpenTelemetrySdk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Everything the full observability product adds on top of the OpenTelemetry-only core: automatic
 * instrumentation, OpenTelemetry Android RUM, and remotely configured sampling.
 *
 * This is the only implementation of [ObservabilityInstrumenting]. Depending on
 * `com.launchdarkly:launchdarkly-otel-android` alone leaves it out of the build entirely, which is
 * what keeps that artifact free of crash handlers, window-callback wrapping, activity lifecycle
 * hooks, and classpath-discovered OpenTelemetry Android instrumentation.
 *
 * @param sdkKey Used to fetch the remote sampling configuration.
 * @param options Decides which detectors are installed.
 * @param logger Internal logging, shared with the core.
 */
internal class DefaultInstrumentation(
    private val sdkKey: String,
    private val options: ObservabilityOptions,
    private val logger: ObserveLogger,
) : ObservabilityInstrumenting {

    private val customSampler = CustomSampler()
    override val sampler: ExportSampler get() = customSampler

    private val samplingApiService = SamplingApiService(
        GraphQLClient(endpoint = options.backendUrl, logger = logger)
    )

    private val scope = CoroutineScope(DispatcherProviderHolder.current.io + SupervisorJob())

    /**
     * The shared touch-capture hook, kept so [install] can start tap detection on the same
     * instance the core is using.
     */
    private var interactions: UserInteractionManaging? = null

    /**
     * Set in [install]. Detectors are constructed before the core finishes building (they are
     * needed to build the SDK), so they reach the emitters through this rather than by capture.
     */
    @Volatile
    private var runtime: ObservabilityRuntime? = null

    override fun createOpenTelemetry(
        application: Application,
        session: LDSessionManaging,
        pipeline: OtelPipelineConfigurator,
    ): OpenTelemetrySdk {
        val rumBuilder = OpenTelemetryRum.builder(application, createOtelRumConfig())
            .addLoggerProviderCustomizer { builder, _ -> pipeline.configureLoggerProvider(builder) }
            .addTracerProviderCustomizer { builder, _ -> pipeline.configureTracerProvider(builder) }
            .addMeterProviderCustomizer { builder, _ -> pipeline.configureMeterProvider(builder) }

        // Use LaunchDarkly's session manager (instead of the RUM SDK's default) so the id RUM's
        // `session.id` appenders stamp is the same one the core, Session Replay, and the OTel-only
        // product all use.
        LDRumSessionManagerAccessor.setSessionManager(rumBuilder, RumSessionManagerAdapter(session))

        if (options.instrumentations.launchTime) {
            addLaunchTimeInstrumentation(application, rumBuilder)
        }

        return rumBuilder.build().openTelemetry as OpenTelemetrySdk
    }

    override fun makeUserInteractionManager(): UserInteractionManaging =
        UserInteractionManager().also { interactions = it }

    override fun makeScreenViewCapture(
        application: Application,
        onScreen: (ScreenView) -> Unit,
    ): ScreenViewCapturing? {
        // The manager is always constructed so Session Replay can register a late-init activity,
        // but automatic detection only starts when the instrumentation is enabled.
        val manager = ScreenViewManager(application, onScreen)
        return if (options.instrumentations.screens) manager else null
    }

    override fun install(runtime: ObservabilityRuntime) {
        this.runtime = runtime

        loadSamplingConfigAsync()

        // The touch-capture hook (wrapping each window's callback plus hit-testing) is invasive, so
        // it is only enabled when something needs it: tap detection here (gated by
        // `instrumentations.userTaps`) or Session Replay, which enables the same shared manager
        // itself. With both off, window callbacks are never wrapped. Whether a detected tap is
        // published as a `click` span is governed separately by `analytics.taps`.
        interactions?.let { touches ->
            if (options.instrumentations.userTaps) {
                touches.enableTouchCapture()
                startTapInstrumentation(scope, touches, runtime)
            }
        }

        // Runs unconditionally so the Session Replay `Launch` breadcrumb is always available; the
        // `app_launch` span is gated by `analytics.appLaunch` inside the runtime.
        AppLaunchTracker(
            application = runtime.application,
            onSignal = { signal -> runtime.recordAppLaunchSignal(signal) },
        ).start()
    }

    private fun createOtelRumConfig(): OtelRumConfig {
        // Session lifetime/rotation is owned by LaunchDarkly's session manager (injected via
        // [LDRumSessionManagerAccessor]), so no SessionConfig is applied here.
        val config = OtelRumConfig()

        if (!options.instrumentations.crashReporting) {
            // Disables [io.opentelemetry.android.instrumentation.crash.CrashReporterInstrumentation]
            config.suppressInstrumentation("crash")
        }

        // Defensively disable the OpenTelemetry Android activity instrumentation
        // ([io.opentelemetry.android.instrumentation.activity.ActivityLifecycleInstrumentation]).
        // We no longer depend on that artifact (see lib/build.gradle.kts), so it is normally not on
        // the classpath; this suppression guards against it being reintroduced transitively by a
        // host. It emits an `AppStart` span plus per-activity lifecycle spans, which are superseded
        // by LaunchDarkly's own `app_launch`, `app_foreground`/`app_background`, and `screen_view`
        // spans, so leaving it on would double-report the same app/screen lifecycle.
        config.suppressInstrumentation("activity")

        return config
    }

    private fun addLaunchTimeInstrumentation(
        application: Application,
        rumBuilder: OpenTelemetryRumBuilder,
    ) {
        rumBuilder.addInstrumentation(
            LaunchTimeInstrumentation(
                application = application,
                metricRecorder = { metric -> runtime?.recordInstrumentationHistogram(metric) },
            )
        )
    }

    private fun loadSamplingConfigAsync() {
        scope.launch {
            val samplingConfig = getSamplingConfig()
            if (samplingConfig != null) {
                logger.info("Sampling configuration was successfully loaded")
            }
            customSampler.setConfig(samplingConfig)
        }
    }

    private suspend fun getSamplingConfig(): SamplingConfig? {
        return try {
            samplingApiService.getSamplingConfig(sdkKey)
        } catch (err: Exception) {
            logger.warn("Failed to get sampling config: ${err.message}")
            null
        }
    }
}
