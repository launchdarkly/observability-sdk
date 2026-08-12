package com.launchdarkly.observability.client

import android.app.Activity
import android.app.Application
import com.launchdarkly.observability.api.ObservabilityOptions
import com.launchdarkly.observability.client.screen.ScreenView
import com.launchdarkly.observability.context.ObserveLogger
import com.launchdarkly.observability.interfaces.Metric
import com.launchdarkly.observability.sampling.ExportSampler
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.logs.SdkLoggerProviderBuilder
import io.opentelemetry.sdk.metrics.SdkMeterProviderBuilder
import io.opentelemetry.sdk.trace.SdkTracerProviderBuilder
import kotlinx.coroutines.flow.SharedFlow

/**
 * Captures raw touches so taps can be turned into `click` spans and replayed as interaction events.
 *
 * Implemented in the full observability product. The OTel-only product never supplies one, which is
 * why it never wraps a window callback or hit-tests a view hierarchy.
 */
interface UserInteractionManaging {
    val touchFlow: SharedFlow<TouchSample>

    /** Supplies the active screen at capture time, so a tap that navigates is stamped correctly. */
    var screenInfoProvider: () -> Pair<String?, String?>

    /** Tracks the current activity and window. Registers lifecycle callbacks only. */
    fun attachToApplication(application: Application)

    /** Begins the invasive part: wrapping window callbacks and hit-testing touches. */
    fun enableTouchCapture()

    fun registerActivity(activity: Activity)
}

/**
 * Detects screen changes automatically from the activity lifecycle.
 *
 * Implemented in the full observability product. The OTel-only product records screen views only
 * when the app calls `trackScreenView` itself.
 */
interface ScreenViewCapturing {
    fun start()

    fun stop()

    fun registerActivity(activity: Activity)

    /** Re-emits the already-resumed screen, e.g. after a session rotation or a late init. */
    fun captureCurrentScreen()
}

/**
 * Applies the LaunchDarkly processors and exporters to the three signal providers.
 *
 * Kept separate from SDK construction so both products share one pipeline definition: the OTel-only
 * product applies it to plain SDK builders, while the full product applies it to the equivalent
 * OpenTelemetry Android RUM customizers.
 */
interface OtelPipelineConfigurator {
    fun configureTracerProvider(builder: SdkTracerProviderBuilder): SdkTracerProviderBuilder

    fun configureLoggerProvider(builder: SdkLoggerProviderBuilder): SdkLoggerProviderBuilder

    fun configureMeterProvider(builder: SdkMeterProviderBuilder): SdkMeterProviderBuilder
}

/**
 * The core pipeline as seen by instrumentation: the emitters and the ambient state instrumentation
 * needs, with no access to the pipeline's internals.
 *
 * Detectors live in the full product and call these emitters; the emitters themselves stay in the
 * core so that a signal recorded manually and the same signal recorded automatically travel exactly
 * the same path.
 */
interface ObservabilityRuntime {
    val application: Application
    val options: ObservabilityOptions
    val session: LDSessionManaging
    val logger: ObserveLogger

    fun getTracer(): Tracer

    /** The screen a signal should be attributed to right now, as `id to name`. */
    val currentScreen: Pair<String?, String?>

    fun recordScreenView(screen: ScreenView)

    fun recordAppLifecycleSignal(signal: AppLifecycleSignal)

    fun recordAppLaunchSignal(signal: AppLaunchSignal)

    /**
     * Emits a `click` span. Timestamps are epoch milliseconds; when null the span is anchored at
     * the current time, which is what the manual `trackClick` API does.
     */
    fun recordClickSpan(attributes: Attributes, startTimeMs: Long?, endTimeMs: Long?)

    /**
     * Records a histogram sample on behalf of instrumentation. Unlike the public
     * `recordHistogram` API this is not gated by [ObservabilityOptions.MetricsApi.enabled], since
     * instrumentation metrics are governed by their own instrumentation flag.
     */
    fun recordInstrumentationHistogram(metric: Metric)
}

/**
 * Supplies automatic instrumentation to the core pipeline. This is the single extension point that
 * separates the two products: the full product provides an implementation, and the OTel-only
 * product provides none, so nothing is installed and nothing is discovered from the classpath.
 */
interface ObservabilityInstrumenting {
    /**
     * Applied to exported spans and logs. The full product returns a sampler driven by remote
     * configuration; without an instrumenting provider the core samples nothing out.
     */
    val sampler: ExportSampler

    /**
     * Builds the OpenTelemetry SDK backing the pipeline. The full product returns an
     * OpenTelemetry Android RUM-backed SDK, which additionally appends screen and network
     * attributes and installs classpath-discovered instrumentation.
     */
    fun createOpenTelemetry(
        application: Application,
        session: LDSessionManaging,
        pipeline: OtelPipelineConfigurator,
    ): OpenTelemetrySdk

    fun makeUserInteractionManager(): UserInteractionManaging

    /** Returns null when automatic screen detection is disabled by configuration. */
    fun makeScreenViewCapture(
        application: Application,
        onScreen: (ScreenView) -> Unit,
    ): ScreenViewCapturing?

    /** Called once the pipeline is live, to start the detectors that feed [ObservabilityRuntime]. */
    fun install(runtime: ObservabilityRuntime)
}
