package com.launchdarkly.observability.api

import com.launchdarkly.observability.BuildConfig
import io.opentelemetry.api.common.Attributes

private const val DEFAULT_OTLP_ENDPOINT = "https://otel.observability.app.launchdarkly.com:4318"
private const val DEFAULT_BACKEND_URL = "https://pub.observability.app.launchdarkly.com"

/**
 * Configuration options for the Observability plugin.
 *
 * @property serviceName The service name for the application. Defaults to the app package name if not set.
 * @property serviceVersion The version of the service. Defaults to the app version if not set.
 * @property otlpEndpoint The OTLP exporter endpoint. Defaults to LaunchDarkly endpoint.
 * @property backendUrl The backend URL for non-OTLP operations. Defaults to LaunchDarkly url.
 * @property resourceAttributes Additional resource attributes to include in telemetry data.
 * @property customHeaders Custom headers to include with OTLP exports.
 * @property sessionTimeout Session timeout in milliseconds. Defaults to 30 * 60 * 1000 (30 minutes).
 * @property debug Enables additional logging if true. Defaults to false.
 * @property disableErrorTracking Disables error tracking if true. Defaults to false.
 * @property disableLogs Disables logs if true. Defaults to false.
 * @property disableTraces Disables traces if true. Defaults to false.
 * @property disableMetrics Disables metrics if true. Defaults to false.
 */
data class Options(
    val serviceName: String = "observability-android",
    val serviceVersion: String = BuildConfig.OBSERVABILITY_SDK_VERSION,
    val otlpEndpoint: String = DEFAULT_OTLP_ENDPOINT,
    val backendUrl: String = DEFAULT_BACKEND_URL,
    val resourceAttributes: Attributes = Attributes.empty(),
    val customHeaders: Map<String, String> = emptyMap(),
    val sessionTimeout: Long = 30 * 60 * 1000L, // 30 minutes
    val debug: Boolean = false,
    val disableErrorTracking: Boolean = false,
    val disableLogs: Boolean = false,
    val disableTraces: Boolean = false,
    val disableMetrics: Boolean = false
) 