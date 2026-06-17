package com.launchdarkly.observability.api

import com.launchdarkly.observability.BuildConfig
import com.launchdarkly.observability.client.TelemetryInspector
import com.launchdarkly.observability.context.LDObserveLogging
import com.launchdarkly.observability.context.ObserveLogAdapter
import io.opentelemetry.api.common.Attributes
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
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
        /**
         * Java-friendly fluent builder for [TracesApi].
         */
        class Builder {
            private var value = TracesApi()

            fun includeErrors(includeErrors: Boolean) = apply { value = value.copy(includeErrors = includeErrors) }
            fun includeSpans(includeSpans: Boolean) = apply { value = value.copy(includeSpans = includeSpans) }

            fun build() = value
        }

        companion object {
            @JvmStatic
            fun enabled() = TracesApi()

            @JvmStatic
            fun disabled() = TracesApi(includeErrors = false, includeSpans = false)

            @JvmStatic
            fun builder() = Builder()
        }
    }

    /**
     * Options for configuring metrics.
     *
     * @property enabled Whether to enable metrics.
     */
    data class MetricsApi(val enabled: Boolean = true) {
        companion object {
            @JvmStatic
            fun enabled() = MetricsApi(true)

            @JvmStatic
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
     * @property trackEvents If `true`, the plugin emits a `track` span when a custom
     *   event is tracked (via the LD `afterTrack` hook or [com.launchdarkly.observability.sdk.LDObserve.track]).
     *   Defaults to `true`.
     * @property screenViews If `true`, the plugin emits a `screen_view` span when a screen is shown
     *   (automatically via [Instrumentations.screens] or manually via
     *   [com.launchdarkly.observability.sdk.LDObserve.trackScreenView]). This flag only gates the
     *   span; automatic screen *detection* (and therefore Session Replay `Navigate` events) is
     *   controlled by [Instrumentations.screens]. Defaults to `true`.
     * @property appLifecycle If `true`, the plugin emits app-lifecycle spans as the app moves
     *   between states: `app_foreground` (with `event.lifecycle_state = foreground`) when it enters
     *   the foreground, and `app_background` (with `event.lifecycle_state = background`) when it
     *   enters the background. This flag only gates the span; the matching Session Replay
     *   `Foreground` / `Background` breadcrumbs are emitted regardless. Defaults to `true`.
     */
    data class Analytics(
        val taps: Boolean = true,
        val trackEvents: Boolean = true,
        val screenViews: Boolean = true,
        val appLifecycle: Boolean = true,
        val appLaunch: Boolean = true,
    ) {
        /**
         * Java-friendly fluent builder for [Analytics].
         */
        class Builder {
            private var value = Analytics()

            fun taps(taps: Boolean) = apply { value = value.copy(taps = taps) }
            fun trackEvents(trackEvents: Boolean) = apply { value = value.copy(trackEvents = trackEvents) }
            fun screenViews(screenViews: Boolean) = apply { value = value.copy(screenViews = screenViews) }
            fun appLifecycle(appLifecycle: Boolean) = apply { value = value.copy(appLifecycle = appLifecycle) }
            fun appLaunch(appLaunch: Boolean) = apply { value = value.copy(appLaunch = appLaunch) }

            fun build() = value
        }

        companion object {
            @JvmStatic
            fun builder() = Builder()
        }
    }

    /**
     * This class allows enabling or disabling specific automatic instrumentations.
     *
     * @property crashReporting If `true`, the plugin will automatically report any uncaught exceptions as errors.
     * @property launchTime If `true`, emits launch-time performance telemetry: the legacy TTID/TTFD
     *   histogram metrics, plus the `app.start` span event on `app_launch` (cold/warm via `start.type`,
     *   with `start.duration_ms`). When `false` the `app.start` event is omitted and the `app_launch`
     *   span is anchored at the launch-detection time (rather than back-dated to process start) so it
     *   carries no startup duration. The `app_launch` span itself is still emitted when
     *   [Analytics.appLaunch] is enabled.
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
    ) {
        /**
         * Java-friendly fluent builder for [Instrumentations].
         */
        class Builder {
            private var value = Instrumentations()

            fun crashReporting(crashReporting: Boolean) = apply { value = value.copy(crashReporting = crashReporting) }
            fun launchTime(launchTime: Boolean) = apply { value = value.copy(launchTime = launchTime) }
            fun userTaps(userTaps: Boolean) = apply { value = value.copy(userTaps = userTaps) }
            fun screens(screens: Boolean) = apply { value = value.copy(screens = screens) }

            fun build() = value
        }

        companion object {
            @JvmStatic
            fun builder() = Builder()

            /** Every automatic instrumentation enabled. */
            @JvmStatic
            fun enabled() = Instrumentations(
                crashReporting = true,
                launchTime = true,
                userTaps = true,
                screens = true,
            )

            /**
             * Every automatic instrumentation disabled. Note this also turns off user-tap detection
             * (so no `click` spans are emitted regardless of [Analytics.taps]) and automatic screen
             * detection (so no `screen_view` / Session Replay `Navigate` events).
             */
            @JvmStatic
            fun disabled() = Instrumentations(
                crashReporting = false,
                launchTime = false,
                userTaps = false,
                screens = false,
            )
        }
    }

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

    /**
     * Java-friendly fluent builder for [ObservabilityOptions].
     *
     * Kotlin callers can keep using the [ObservabilityOptions] constructor with named/default
     * arguments. This builder exists so Java callers, which cannot omit Kotlin default parameters,
     * can configure only the options they care about. Every setter defaults to the same value as
     * the [ObservabilityOptions] primary constructor.
     *
     * ```java
     * ObservabilityOptions options = ObservabilityOptions.builder()
     *     .debug(true)
     *     .otlpEndpoint(BuildConfig.OTLP_ENDPOINT)
     *     .instrumentations(
     *         ObservabilityOptions.Instrumentations.builder()
     *             .launchTime(true)
     *             .build())
     *     .build();
     * ```
     */
    class Builder {
        private var options = ObservabilityOptions()

        fun enabled(enabled: Boolean) = apply { options = options.copy(enabled = enabled) }
        fun serviceName(serviceName: String) = apply { options = options.copy(serviceName = serviceName) }
        fun serviceVersion(serviceVersion: String) = apply { options = options.copy(serviceVersion = serviceVersion) }
        fun otlpEndpoint(otlpEndpoint: String) = apply { options = options.copy(otlpEndpoint = otlpEndpoint) }
        fun backendUrl(backendUrl: String) = apply { options = options.copy(backendUrl = backendUrl) }
        fun contextFriendlyName(contextFriendlyName: String?) = apply { options = options.copy(contextFriendlyName = contextFriendlyName) }
        fun resourceAttributes(resourceAttributes: Attributes) = apply { options = options.copy(resourceAttributes = resourceAttributes) }
        fun customHeaders(customHeaders: Map<String, String>) = apply { options = options.copy(customHeaders = customHeaders) }

        /** Sets the session background timeout in milliseconds (Java-friendly overload). */
        fun sessionBackgroundTimeoutMillis(millis: Long) = apply {
            options = options.copy(sessionBackgroundTimeout = millis.milliseconds)
        }
        fun sessionBackgroundTimeout(sessionBackgroundTimeout: Duration) = apply {
            options = options.copy(sessionBackgroundTimeout = sessionBackgroundTimeout)
        }
        fun debug(debug: Boolean) = apply { options = options.copy(debug = debug) }
        fun logsApiLevel(logsApiLevel: LogLevel) = apply { options = options.copy(logsApiLevel = logsApiLevel) }
        fun tracesApi(tracesApi: TracesApi) = apply { options = options.copy(tracesApi = tracesApi) }
        fun metricsApi(metricsApi: MetricsApi) = apply { options = options.copy(metricsApi = metricsApi) }
        fun analytics(analytics: Analytics) = apply { options = options.copy(analytics = analytics) }
        fun instrumentations(instrumentations: Instrumentations) = apply { options = options.copy(instrumentations = instrumentations) }
        fun logAdapter(logAdapter: ObserveLogAdapter) = apply { options = options.copy(logAdapter = logAdapter) }
        fun loggerName(loggerName: String) = apply { options = options.copy(loggerName = loggerName) }
        fun telemetryInspector(telemetryInspector: TelemetryInspector?) = apply { options = options.copy(telemetryInspector = telemetryInspector) }

        fun build() = options
    }

    companion object {
        @JvmStatic
        fun builder() = Builder()
    }
}
