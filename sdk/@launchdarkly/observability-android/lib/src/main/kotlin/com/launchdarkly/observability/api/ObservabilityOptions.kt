package com.launchdarkly.observability.api

import com.launchdarkly.logging.LDLogAdapter
import com.launchdarkly.observability.BuildConfig
import com.launchdarkly.sdk.android.LDTimberLogging
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
 * @property instrumentations Options for configuring automatic instrumentations. See [Instrumentations].
 * @property logAdapter The log adapter to use. Defaults to using the LaunchDarkly SDK's LDTimberLogging.adapter(). Use LDAndroidLogging.adapter() to use the Android logging adapter.
 * @property loggerName The name of the logger to use. Defaults to "LaunchDarklyObservabilityPlugin".
 */
data class ObservabilityOptions(
    val serviceName: String = DEFAULT_SERVICE_NAME,
    val serviceVersion: String = BuildConfig.OBSERVABILITY_SDK_VERSION,
    val otlpEndpoint: String = DEFAULT_OTLP_ENDPOINT,
    val backendUrl: String = DEFAULT_BACKEND_URL,
    val resourceAttributes: Attributes = Attributes.empty(),
    val customHeaders: Map<String, String> = emptyMap(),
    val sessionBackgroundTimeout: Duration = 15.minutes,
    val debug: Boolean = false,
    val logsApiLevel: LogLevel = LogLevel.INFO,
    val tracesApi: TracesApi = TracesApi.enabled(),
    val metricsApi: MetricsApi = MetricsApi.enabled(),
    val instrumentations: Instrumentations = Instrumentations(),
    val logAdapter: LDLogAdapter = LDTimberLogging.adapter(), // This follows the LaunchDarkly SDK's default log adapter
    val loggerName: String = "LaunchDarklyObservabilityPlugin",
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
     * This class allows enabling or disabling specific automatic instrumentations.
     *
     * @property crashReporting If `true`, the plugin will automatically report any uncaught exceptions as errors.
     * @property activityLifecycle If `true`, the plugin will automatically start spans for Android Activity lifecycle events.
     * @property launchTime If `true`, the plugin will automatically measure and report the application's startup time as metrics.
     */
    data class Instrumentations(
        val crashReporting: Boolean = true,
        val activityLifecycle: Boolean = true,
        val launchTime: Boolean = false,
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
