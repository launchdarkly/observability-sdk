package com.launchdarkly.observability.api

import io.opentelemetry.api.common.Attributes

/**
 * Configuration options for the Observability plugin.
 *
 * @property serviceName The service name for the application. Defaults to the app package name if not set.
 * @property serviceVersion The version of the service. Defaults to the app version if not set.
 * @property otlpEndpoint The OTLP exporter endpoint. Defaults to 'https://otel.observability.app.launchdarkly.com:4318'.
 * @property backendUrl The backend URL for non-OTLP operations. Defaults to 'https://pub.observability.app.launchdarkly.com'.
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
    val serviceName: String? = null,
    val serviceVersion: String? = null,
    val otlpEndpoint: String = "https://otel.observability.app.launchdarkly.com:4318",
    val backendUrl: String = "https://pub.observability.app.launchdarkly.com",
    val resourceAttributes: Attributes = Attributes.empty(),
    val customHeaders: Map<String, String> = emptyMap(),
    val sessionTimeout: Long = 30 * 60 * 1000L, // 30 minutes
    val debug: Boolean = false,
    val disableErrorTracking: Boolean = false,
    val disableLogs: Boolean = false,
    val disableTraces: Boolean = false,
    val disableMetrics: Boolean = false
) 