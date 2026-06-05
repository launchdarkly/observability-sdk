package com.launchdarkly.observability.api

import com.launchdarkly.observability.BuildConfig
import com.launchdarkly.observability.client.TelemetryInspector
import com.launchdarkly.observability.context.LDObserveLogging
import com.launchdarkly.observability.context.ObserveLogAdapter
import io.opentelemetry.api.common.Attributes
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

const val DEFAULT_SERVICE_NAME = "observability-android"
const val DEFAULT_OTLP_ENDPOINT = "https://otel.observability.app.launchdarkly.com:4318"
const val DEFAULT_BACKEND_URL = "https://pub.observability.app.launchdarkly.com"

/**
 * Configuration options for the Observability plugin.
 *
 * @property serviceName The service name for the application. Defaults to [DEFAULT_SERVICE_NAME].
 * @property serviceVersion The version of the service. Defaults to the SDK version.
 * @property otlpEndpoint The OTLP exporter endpoint. Defaults to LaunchDarkly endpoint [DEFAULT_OTLP_ENDPOINT].
 * @property backendUrl The backend URL for non-OTLP operations. Defaults to LaunchDarkly url [DEFAULT_BACKEND_URL].
 * @property resourceAttributes Additional resource attributes to include in telemetry data.
 * @property customHeaders Custom headers to include with OTLP exports.
 * @property sessionBackgroundTimeout Session timeout if app is backgrounded. Defaults to 15 minutes.
 * @property debug Enables verbose internal logging if true as well as other debug functionality. Defaults to false.
 * @property logsApiLevel Level for logs to be exported. Defaults to INFO. Set to NONE to disable log exporting.
 * @property tracesApi Options for configuring traces. See [TracesApi]. Tracing is enabled by default.
 * @property metricsApi Options for configuring metrics. See [MetricsApi]. Metrics are enabled by default.
 * @property analytics Options for configuring analytics telemetry. See [Analytics].
 * @property instrumentations Options for configuring automatic instrumentations. See [Instrumentations].
 * @property logAdapter The log adapter to use. Defaults to [LDObserveLogging.adapter] which writes to Android's native Log API.
 * @property loggerName The name of the logger to use. Defaults to "LaunchDarklyObservabilityPlugin".
 * @property telemetryInspector Optional [TelemetryInspector] for intercepting exported telemetry during testing.
 *   When provided together with [debug] = true, the inspector's exporters are wired into composite
 *   exporters so that test code can assert on the data that flows through the SDK.
 */
data class ObservabilityOptions(
    val enabled: Boolean = true,
    val serviceName: String = DEFAULT_SERVICE_NAME,
    val serviceVersion: String = BuildConfig.OBSERVABILITY_SDK_VERSION,
    val otlpEndpoint: String = DEFAULT_OTLP_ENDPOINT,
    val backendUrl: String = DEFAULT_BACKEND_URL,
    val contextFriendlyName: String? = null,
    val resourceAttributes: Attributes = Attributes.empty(),
    val customHeaders: Map<String, String> = emptyMap(),
    val sessionBackgroundTimeout: Duration = 15.minutes,
    val debug: Boolean = false,
    val logsApiLevel: LogLevel = LogLevel.INFO,
    val tracesApi: TracesApi = TracesApi.enabled(),
    val metricsApi: MetricsApi = MetricsApi.enabled(),
    val analytics: Analytics = Analytics(),
    val instrumentations: Instrumentations = Instrumentations(),
    val logAdapter: ObserveLogAdapter = LDObserveLogging.adapter(),
    val loggerName: String = "LaunchDarklyObservabilityPlugin",
    val telemetryInspector: TelemetryInspector? = null,
){
    /**
     * Options for configuring traces.
     *
     * @property includeErrors Whether to automatically instrument for and record errors and exceptions as spans.
     * @property includeSpans Whether to automatically instrument for and record UI performance and other events as spans.
     */
    data class TracesApi(
        val includeErrors: Boolean = true,
        val includeSpans: Boolean = true
    ) {
        companion object {
            fun enabled() = TracesApi()
            fun disabled() = TracesApi(includeErrors = false, includeSpans = false)
        }
    }

    /**
     * Options for configuring metrics.
     *
     * @property enabled Whether to enable metrics.
     */
    data class MetricsApi(val enabled: Boolean = true) {
        companion object {
            fun enabled() = MetricsApi(true)
            fun disabled() = MetricsApi(false)
        }
    }

    /**
     * Options for configuring analytics telemetry. These signals are emitted as
     * OpenTelemetry spans.
     *
     * @property taps If `true`, the plugin publishes a `click` span for each detected tap.
     *   Tap detection itself is governed by [Instrumentations.userTaps]; if that is disabled
     *   no taps are issued and this flag has no effect. Defaults to `true`.
     * @property pageViews If `true`, the plugin starts spans for Android Activity lifecycle events
     *   (screen/page views). Defaults to `true`.
     * @property trackEvents If `true`, the plugin emits a `track` span when a custom
     *   event is tracked (via the LD `afterTrack` hook or [com.launchdarkly.observability.sdk.LDObserve.track]).
     *   Defaults to `true`.
     * @property screenViews If `true`, the plugin emits a `screen_view` span when a screen is shown
     *   (automatically via [Instrumentations.screens] or manually via
     *   [com.launchdarkly.observability.sdk.LDObserve.trackScreenView]). This flag only gates the
     *   span; automatic screen *detection* (and therefore Session Replay `Navigate` events) is
     *   controlled by [Instrumentations.screens]. Defaults to `true`.
     */
    data class Analytics(
        val taps: Boolean = true,
        val pageViews: Boolean = true,
        val trackEvents: Boolean = true,
        val screenViews: Boolean = true,
    )

    /**
     * This class allows enabling or disabling specific automatic instrumentations.
     *
     * @property crashReporting If `true`, the plugin will automatically report any uncaught exceptions as errors.
     * @property launchTime If `true`, the plugin will automatically measure and report the application's startup time as metrics.
     * @property userTaps If `true`, the plugin runs the tap-detection machinery, issuing tap events
     *   from the captured touch stream. Publishing those taps as `click` spans is governed
     *   separately by [Analytics.taps]; Session Replay capture is unaffected by either flag.
     *   Defaults to `true`.
     * @property screens If `true`, the plugin automatically detects screen changes via Android Activity
     *   lifecycle callbacks. This drives the `screen_view` span (gated separately by
     *   [Analytics.screenViews]) and Session Replay `Navigate` events. Defaults to `true`.
     */
    data class Instrumentations(
        val crashReporting: Boolean = true,
        val launchTime: Boolean = false,
        val userTaps: Boolean = true,
        val screens: Boolean = true,
    )

    /**
     * Defines the logging levels for telemetry data. These levels correspond to the OpenTelemetry Log Severity.
     *
     * The levels are ordered by severity, from `TRACE` (least severe) to `FATAL` (most severe).
     * Setting a `logsApiLevel` in [ObservabilityOptions] to a specific level means that
     * logs of that level and all higher severity levels will be exported.
     *
     * For instance, setting the level to `INFO` will cause `INFO`, `WARN`, `ERROR`, and `FATAL`
     * logs (and their variants) to be exported, while `TRACE` and `DEBUG` logs will be ignored.
     *
     * The `NONE` level can be used to disable log exporting entirely.
     *
     * @see <a href="https://opentelemetry.io/docs/specs/otel/logs/data-model/#severity-fields">OpenTelemetry Log Data Model - Severity</a>
     *
     * @property level The integer representation of the log level, as defined by OpenTelemetry.
     */
    enum class LogLevel(val level: Int) {
        TRACE(1),
        TRACE2(2),
        TRACE3(3),
        TRACE4(4),
        DEBUG(5),
        DEBUG2(6),
        DEBUG3(7),
        DEBUG4(8),
        INFO(9),
        INFO2(10),
        INFO3(11),
        INFO4(12),
        WARN(13),
        WARN2(14),
        WARN3(15),
        WARN4(16),
        ERROR(17),
        ERROR2(18),
        ERROR3(19),
        ERROR4(20),
        FATAL(21),
        FATAL2(22),
        FATAL3(23),
        FATAL4(24),
        NONE(Int.MAX_VALUE)
    }
}
