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
            private var includeErrors: Boolean = true
            private var includeSpans: Boolean = true

            fun includeErrors(includeErrors: Boolean) = apply { this.includeErrors = includeErrors }
            fun includeSpans(includeSpans: Boolean) = apply { this.includeSpans = includeSpans }

            fun build() = TracesApi(includeErrors = includeErrors, includeSpans = includeSpans)
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
    ) {
        /**
         * Java-friendly fluent builder for [Analytics].
         */
        class Builder {
            private var taps: Boolean = true
            private var pageViews: Boolean = true
            private var trackEvents: Boolean = true
            private var screenViews: Boolean = true

            fun taps(taps: Boolean) = apply { this.taps = taps }
            fun pageViews(pageViews: Boolean) = apply { this.pageViews = pageViews }
            fun trackEvents(trackEvents: Boolean) = apply { this.trackEvents = trackEvents }
            fun screenViews(screenViews: Boolean) = apply { this.screenViews = screenViews }

            fun build() = Analytics(
                taps = taps,
                pageViews = pageViews,
                trackEvents = trackEvents,
                screenViews = screenViews,
            )
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
    ) {
        /**
         * Java-friendly fluent builder for [Instrumentations].
         */
        class Builder {
            private var crashReporting: Boolean = true
            private var launchTime: Boolean = false
            private var userTaps: Boolean = true
            private var screens: Boolean = true

            fun crashReporting(crashReporting: Boolean) = apply { this.crashReporting = crashReporting }
            fun launchTime(launchTime: Boolean) = apply { this.launchTime = launchTime }
            fun userTaps(userTaps: Boolean) = apply { this.userTaps = userTaps }
            fun screens(screens: Boolean) = apply { this.screens = screens }

            fun build() = Instrumentations(
                crashReporting = crashReporting,
                launchTime = launchTime,
                userTaps = userTaps,
                screens = screens,
            )
        }

        companion object {
            @JvmStatic
            fun builder() = Builder()
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
        private var enabled: Boolean = true
        private var serviceName: String = DEFAULT_SERVICE_NAME
        private var serviceVersion: String = BuildConfig.OBSERVABILITY_SDK_VERSION
        private var otlpEndpoint: String = DEFAULT_OTLP_ENDPOINT
        private var backendUrl: String = DEFAULT_BACKEND_URL
        private var contextFriendlyName: String? = null
        private var resourceAttributes: Attributes = Attributes.empty()
        private var customHeaders: Map<String, String> = emptyMap()
        private var sessionBackgroundTimeout: Duration = 15.minutes
        private var debug: Boolean = false
        private var logsApiLevel: LogLevel = LogLevel.INFO
        private var tracesApi: TracesApi = TracesApi.enabled()
        private var metricsApi: MetricsApi = MetricsApi.enabled()
        private var analytics: Analytics = Analytics()
        private var instrumentations: Instrumentations = Instrumentations()
        private var logAdapter: ObserveLogAdapter = LDObserveLogging.adapter()
        private var loggerName: String = "LaunchDarklyObservabilityPlugin"
        private var telemetryInspector: TelemetryInspector? = null

        fun enabled(enabled: Boolean) = apply { this.enabled = enabled }
        fun serviceName(serviceName: String) = apply { this.serviceName = serviceName }
        fun serviceVersion(serviceVersion: String) = apply { this.serviceVersion = serviceVersion }
        fun otlpEndpoint(otlpEndpoint: String) = apply { this.otlpEndpoint = otlpEndpoint }
        fun backendUrl(backendUrl: String) = apply { this.backendUrl = backendUrl }
        fun contextFriendlyName(contextFriendlyName: String?) = apply { this.contextFriendlyName = contextFriendlyName }
        fun resourceAttributes(resourceAttributes: Attributes) = apply { this.resourceAttributes = resourceAttributes }
        fun customHeaders(customHeaders: Map<String, String>) = apply { this.customHeaders = customHeaders }

        /** Sets the session background timeout in milliseconds (Java-friendly overload). */
        fun sessionBackgroundTimeoutMillis(millis: Long) = apply {
            this.sessionBackgroundTimeout = millis.milliseconds
        }
        fun sessionBackgroundTimeout(sessionBackgroundTimeout: Duration) = apply {
            this.sessionBackgroundTimeout = sessionBackgroundTimeout
        }
        fun debug(debug: Boolean) = apply { this.debug = debug }
        fun logsApiLevel(logsApiLevel: LogLevel) = apply { this.logsApiLevel = logsApiLevel }
        fun tracesApi(tracesApi: TracesApi) = apply { this.tracesApi = tracesApi }
        fun metricsApi(metricsApi: MetricsApi) = apply { this.metricsApi = metricsApi }
        fun analytics(analytics: Analytics) = apply { this.analytics = analytics }
        fun instrumentations(instrumentations: Instrumentations) = apply { this.instrumentations = instrumentations }
        fun logAdapter(logAdapter: ObserveLogAdapter) = apply { this.logAdapter = logAdapter }
        fun loggerName(loggerName: String) = apply { this.loggerName = loggerName }
        fun telemetryInspector(telemetryInspector: TelemetryInspector?) = apply { this.telemetryInspector = telemetryInspector }

        fun build() = ObservabilityOptions(
            enabled = enabled,
            serviceName = serviceName,
            serviceVersion = serviceVersion,
            otlpEndpoint = otlpEndpoint,
            backendUrl = backendUrl,
            contextFriendlyName = contextFriendlyName,
            resourceAttributes = resourceAttributes,
            customHeaders = customHeaders,
            sessionBackgroundTimeout = sessionBackgroundTimeout,
            debug = debug,
            logsApiLevel = logsApiLevel,
            tracesApi = tracesApi,
            metricsApi = metricsApi,
            analytics = analytics,
            instrumentations = instrumentations,
            logAdapter = logAdapter,
            loggerName = loggerName,
            telemetryInspector = telemetryInspector,
        )
    }

    companion object {
        @JvmStatic
        fun builder() = Builder()
    }
}
