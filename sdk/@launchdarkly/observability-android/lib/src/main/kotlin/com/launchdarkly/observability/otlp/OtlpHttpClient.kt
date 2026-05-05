package com.launchdarkly.observability.otlp

import com.launchdarkly.observability.BuildConfig
import com.launchdarkly.observability.coroutines.DispatcherProviderHolder
import com.launchdarkly.observability.network.GzipUtil
import com.launchdarkly.observability.network.UrlConnectionProvider
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlin.time.Duration

/**
 * Thin OTLP/HTTP+JSON client. Encodes a payload with kotlinx-serialization, optionally
 * gzip-compresses it, and POSTs it to the configured endpoint.
 *
 * Mirrors the Swift `OtlpHttpClient`. Non-2xx HTTP statuses surface as [IOException] so
 * callers (e.g. `BatchWorker`) can treat them as retryable export failures.
 */
class OtlpHttpClient(
    private val endpoint: String,
    private val config: OtlpConfiguration = OtlpConfiguration(),
    private val connectionProvider: UrlConnectionProvider = DEFAULT_CONNECTION_PROVIDER,
    private val json: Json = DEFAULT_JSON,
) {
    /**
     * Encodes [body] as OTLP/JSON and sends it to the configured endpoint.
     *
     * @param body The payload to serialize and send.
     * @param serializer Explicit serializer for [T] (required for generic `T`).
     * @param explicitTimeout Optional timeout override; the effective timeout is
     *   `min(explicitTimeout, config.timeout)`.
     *
     * @throws IOException if the HTTP response status is not 2xx, or any IO error occurs.
     */
    suspend fun <T> send(
        body: T,
        serializer: KSerializer<T>,
        explicitTimeout: Duration? = null,
    ) = withContext(DispatcherProviderHolder.current.io) {
        val rawBytes = json.encodeToString(serializer, body).toByteArray(Charsets.UTF_8)
        val useGzip = config.compression == OtlpCompression.GZIP
        val payloadBytes = if (useGzip) GzipUtil.gzip(rawBytes) else rawBytes

        val effectiveTimeoutMs = minOf(
            explicitTimeout?.inWholeMilliseconds ?: Long.MAX_VALUE,
            config.timeout.inWholeMilliseconds,
        ).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()

        var connection: HttpURLConnection? = null
        try {
            val conn = connectionProvider.openConnection(endpoint).also { connection = it }
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.connectTimeout = effectiveTimeoutMs
            conn.readTimeout = effectiveTimeoutMs
            conn.setRequestProperty(HEADER_CONTENT_TYPE, CONTENT_TYPE_JSON)
            conn.setRequestProperty(HEADER_USER_AGENT, userAgent)
            if (useGzip) {
                conn.setRequestProperty(HEADER_CONTENT_ENCODING, "gzip")
            }
            config.headers.forEach { (key, value) ->
                conn.setRequestProperty(key, value)
            }
            conn.setFixedLengthStreamingMode(payloadBytes.size)

            conn.outputStream.use { it.write(payloadBytes) }

            val status = conn.responseCode
            if (status !in 200..299) {
                val errorBody = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                throw IOException("OTLP export HTTP $status: $errorBody")
            }
            // Drain the body to release the underlying connection for keep-alive.
            conn.inputStream.use { it.readBytes() }
        } finally {
            connection?.disconnect()
        }
    }

    companion object {
        internal const val OTLP_VERSION = "0.20.0"
        internal const val HEADER_CONTENT_TYPE = "Content-Type"
        internal const val HEADER_CONTENT_ENCODING = "Content-Encoding"
        internal const val HEADER_USER_AGENT = "User-Agent"
        internal const val CONTENT_TYPE_JSON = "application/json"

        /**
         * OTLP user-agent string per
         * [the spec](https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/protocol/exporter.md#user-agent).
         */
        internal val userAgent: String = run {
            val sdk = BuildConfig.OBSERVABILITY_SDK_VERSION.removePrefix("v")
            "OTel-OTLP-Exporter-Kotlin/$OTLP_VERSION LaunchDarkly-Observability-Android/$sdk"
        }

        internal val DEFAULT_JSON: Json = Json {
            encodeDefaults = false
            explicitNulls = false
        }

        internal val DEFAULT_CONNECTION_PROVIDER: UrlConnectionProvider =
            object : UrlConnectionProvider {
                override fun openConnection(url: String): HttpURLConnection =
                    URL(url).openConnection() as HttpURLConnection
            }
    }
}
