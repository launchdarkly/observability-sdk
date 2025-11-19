package com.launchdarkly.observability.api

import com.launchdarkly.logging.LDLogAdapter
import com.launchdarkly.observability.BuildConfig
import com.launchdarkly.observability.interfaces.LDExtendedInstrumentation
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
 * @property serviceName The service name for the application. Defaults to the app package name if not set.
 * @property serviceVersion The version of the service. Defaults to the app version if not set.
 * @property otlpEndpoint The OTLP exporter endpoint. Defaults to LaunchDarkly endpoint.
 * @property backendUrl The backend URL for non-OTLP operations. Defaults to LaunchDarkly url.
 * @property resourceAttributes Additional resource attributes to include in telemetry data.
 * @property customHeaders Custom headers to include with OTLP exports.
 * @property sessionBackgroundTimeout Session timeout if app is backgrounded. Defaults to 15 minutes.
 * @property debug Enables verbose telemetry logging if true as well as other debug functionality. Defaults to false.
 * @property disableErrorTracking Disables error tracking if true. Defaults to false.
 * @property disableLogs Disables logs if true. Defaults to false.
 * @property disableTraces Disables traces if true. Defaults to false.
 * @property disableMetrics Disables metrics if true. Defaults to false.
 * @property logAdapter The log adapter to use. Defaults to using the LaunchDarkly SDK's LDTimberLogging.adapter(). Use LDAndroidLogging.adapter() to use the Android logging adapter.
 * @property loggerName The name of the logger to use. Defaults to "LaunchDarklyObservabilityPlugin".
 * @property instrumentations List of additional instrumentations to use
 */
data class Options @JvmOverloads constructor(
    val serviceName: String = DEFAULT_SERVICE_NAME,
    val serviceVersion: String = BuildConfig.OBSERVABILITY_SDK_VERSION,
    val otlpEndpoint: String = DEFAULT_OTLP_ENDPOINT,
    val backendUrl: String = DEFAULT_BACKEND_URL,
    val resourceAttributes: Attributes = Attributes.empty(),
    val customHeaders: Map<String, String> = emptyMap(),
    val sessionBackgroundTimeout: Duration = 15.minutes,
    val debug: Boolean = false,
    val disableErrorTracking: Boolean = false,
    val disableLogs: Boolean = false,
    val disableTraces: Boolean = false,
    val disableMetrics: Boolean = false,
    val logAdapter: LDLogAdapter = LDTimberLogging.adapter(), // this follows the LaunchDarkly SDK's default log adapter
    val loggerName: String = "LaunchDarklyObservabilityPlugin",
    // TODO: update this to provide a list of factories instead of instances
    val instrumentations: List<LDExtendedInstrumentation> = emptyList()
) {
    companion object {
        @JvmStatic
        fun builder(): OptionsBuilder = OptionsBuilder()
    }
}

/**
 * Java-friendly builder for [Options].
 * @example
 * ```java
 * Options options = Options.builder()
 *     .serviceName("example-service")
 *     .backendUrl("https://example.com")
 *     .build();
 * ```
 */
class OptionsBuilder {
    private var serviceName: String = DEFAULT_SERVICE_NAME
    private var serviceVersion: String = BuildConfig.OBSERVABILITY_SDK_VERSION
    private var otlpEndpoint: String = DEFAULT_OTLP_ENDPOINT
    private var backendUrl: String = DEFAULT_BACKEND_URL
    private var resourceAttributes: Attributes = Attributes.empty()
    private var customHeaders: Map<String, String> = emptyMap()
    private var sessionBackgroundTimeout: Duration = 15.minutes
    private var debug: Boolean = false
    private var disableErrorTracking: Boolean = false
    private var disableLogs: Boolean = false
    private var disableTraces: Boolean = false
    private var disableMetrics: Boolean = false
    private var logAdapter: LDLogAdapter = LDTimberLogging.adapter()
    private var loggerName: String = "LaunchDarklyObservabilityPlugin"
    private var instrumentations: List<LDExtendedInstrumentation> = emptyList()

    fun serviceName(value: String) = apply { this.serviceName = value }
    fun serviceVersion(value: String) = apply { this.serviceVersion = value }
    fun otlpEndpoint(value: String) = apply { this.otlpEndpoint = value }
    fun backendUrl(value: String) = apply { this.backendUrl = value }
    fun resourceAttributes(value: Attributes) = apply { this.resourceAttributes = value }
    fun customHeaders(value: Map<String, String>) = apply { this.customHeaders = value }

    /**
     * Set the session background timeout using Kotlin Duration (for Kotlin callers).
     */
    fun sessionBackgroundTimeout(value: Duration) = apply { this.sessionBackgroundTimeout = value }

    /**
     * Set the session background timeout using java.time.Duration (for Java callers).
     */
    fun sessionBackgroundTimeout(value: java.time.Duration) = apply {
        this.sessionBackgroundTimeout = Duration.Companion.milliseconds(value.toMillis())
    }

    /**
     * Set the session background timeout in milliseconds (for Java callers).
     */
    fun sessionBackgroundTimeoutMillis(value: Long) = apply {
        this.sessionBackgroundTimeout = Duration.Companion.milliseconds(value)
    }

    fun debug(value: Boolean) = apply { this.debug = value }
    fun disableErrorTracking(value: Boolean) = apply { this.disableErrorTracking = value }
    fun disableLogs(value: Boolean) = apply { this.disableLogs = value }
    fun disableTraces(value: Boolean) = apply { this.disableTraces = value }
    fun disableMetrics(value: Boolean) = apply { this.disableMetrics = value }
    fun logAdapter(value: LDLogAdapter) = apply { this.logAdapter = value }
    fun loggerName(value: String) = apply { this.loggerName = value }
    fun instrumentations(value: List<LDExtendedInstrumentation>) = apply { this.instrumentations = value }

    fun build(): Options =
        Options(
            serviceName = serviceName,
            serviceVersion = serviceVersion,
            otlpEndpoint = otlpEndpoint,
            backendUrl = backendUrl,
            resourceAttributes = resourceAttributes,
            customHeaders = customHeaders,
            sessionBackgroundTimeout = sessionBackgroundTimeout,
            debug = debug,
            disableErrorTracking = disableErrorTracking,
            disableLogs = disableLogs,
            disableTraces = disableTraces,
            disableMetrics = disableMetrics,
            logAdapter = logAdapter,
            loggerName = loggerName,
            instrumentations = instrumentations
        )
}

// No top-level factory object needed; use Options.builder() from Java
