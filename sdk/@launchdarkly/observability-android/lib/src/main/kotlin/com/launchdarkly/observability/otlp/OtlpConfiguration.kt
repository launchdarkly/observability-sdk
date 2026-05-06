package com.launchdarkly.observability.otlp

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Compression type applied to OTLP/HTTP request bodies.
 */
enum class OtlpCompression {
    GZIP,
    NONE,
}

/**
 * Configuration for [OtlpHttpClient].
 *
 * Mirrors the Swift `OtlpConfiguration` struct.
 *
 * @property timeout Maximum duration to wait for an OTLP/HTTP request to complete.
 * @property compression Compression to apply to request bodies. Defaults to [OtlpCompression.GZIP].
 * @property headers Additional static headers to attach to every OTLP/HTTP request.
 */
data class OtlpConfiguration(
    val timeout: Duration = DEFAULT_TIMEOUT,
    val compression: OtlpCompression = OtlpCompression.GZIP,
    val headers: Map<String, String> = emptyMap(),
) {
    companion object {
        val DEFAULT_TIMEOUT: Duration = 10.seconds
    }
}
