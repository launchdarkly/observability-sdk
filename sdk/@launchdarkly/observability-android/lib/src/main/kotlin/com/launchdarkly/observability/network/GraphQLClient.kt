package com.launchdarkly.observability.network

import com.launchdarkly.observability.context.ObserveLogger
import com.launchdarkly.observability.coroutines.DispatcherProviderHolder
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.JsonElement
import java.net.HttpURLConnection
import java.net.URL

@Serializable
data class GraphQLRequest(
    val query: String,
    val variables: Map<String, JsonElement> = emptyMap()
)

@Serializable
data class GraphQLResponse<T>(
    val data: T?,
    val errors: List<GraphQLError>? = null,
    /**
     * The HTTP status the response was read from, or `null` when the request never reached one (a
     * timeout, or connectivity loss). Not part of the GraphQL envelope: [GraphQLClient] fills it in so
     * callers can classify a failure with [ErrorRecoverability] instead of parsing [GraphQLError.message].
     */
    @Transient val httpStatusCode: Int? = null
)

@Serializable
data class GraphQLError(
    val message: String,
    val locations: List<GraphQLLocation>? = null,
    val path: List<String>? = null,
    val extensions: GraphQLErrorExtensions? = null
)

/**
 * Server-supplied metadata about a [GraphQLError].
 */
@Serializable
data class GraphQLErrorExtensions(
    /** Machine-readable error identifier, e.g. `SESSION_REPLAY_BLOCKED_IN_REGION`. */
    val code: String? = null,
    /**
     * The server's own verdict on whether retrying the operation can succeed. Takes precedence over
     * any status-code based classification when present.
     */
    val retryable: Boolean? = null
)

/**
 * Errors-only view of the GraphQL envelope. The public graph also returns this shape alongside a
 * non-2xx status, where `data` is absent, so error metadata can still be read from the raw body.
 */
@Serializable
private data class GraphQLErrorEnvelope(
    val errors: List<GraphQLError>? = null
)

@Serializable
data class GraphQLLocation(
    val line: Int,
    val column: Int
)

interface UrlConnectionProvider {
    fun openConnection(url: String): HttpURLConnection
}

/**
 * Generic GraphQL client for making HTTP requests to GraphQL endpoints
 */
class GraphQLClient(
    val endpoint: String,
    val headers: Map<String, String> = emptyMap(),
    private val logger: ObserveLogger,
    private val json: Json = Json {
        isLenient = true
        ignoreUnknownKeys = true
    },
    private val connectionProvider: UrlConnectionProvider = object : UrlConnectionProvider {
        override fun openConnection(url: String): HttpURLConnection {
            return URL(url).openConnection() as HttpURLConnection
        }
    }
) {

    companion object {
        private const val CONNECT_TIMEOUT = 10000
        private const val READ_TIMEOUT = 10000
    }

    /**
     * Executes a GraphQL query
     * @param query The GraphQL query string
     * @param variables Query variables
     * @param dataSerializer Kotlinx serialization serializer for the expected response data type
     * @return GraphQLResponse containing either the deserialized data or error information
     */
    suspend fun <T> execute(
        query: String,
        variables: Map<String, JsonElement> = emptyMap(),
        dataSerializer: KSerializer<T>,
        compress: Boolean = true
    ): GraphQLResponse<T> = withContext(DispatcherProviderHolder.current.io) {
        var connection: HttpURLConnection? = null
        val response: GraphQLResponse<T> = try {
            val request = GraphQLRequest(
                query = query,
                variables = variables
            )

            val requestJson = json.encodeToString(GraphQLRequest.serializer(), request)
            val requestBytes = requestJson.toByteArray(Charsets.UTF_8)
            val payloadBytes = if (compress) GzipUtil.gzip(requestBytes) else requestBytes
            val connectionLocal = connectionProvider.openConnection(endpoint).also { connection = it }

            connectionLocal.apply {
                requestMethod = "POST"
                setRequestProperty("Content-Length", payloadBytes.size.toString())
                setRequestProperty("Content-Type", "application/json")
                if (compress) {
                    setRequestProperty("Content-Encoding", "gzip")
                }

                // Add custom headers
                headers.forEach { (key, value) ->
                    setRequestProperty(key, value)
                }

                doOutput = true
                connectTimeout = CONNECT_TIMEOUT
                readTimeout = READ_TIMEOUT
                setFixedLengthStreamingMode(payloadBytes.size)
            }

            // Send request
            connectionLocal.outputStream.use { outputStream ->
                outputStream.write(payloadBytes)
            }

            // Read response
            val responseCode = connectionLocal.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                val errorText = connectionLocal.errorStream?.bufferedReader()?.use { it.readText() } ?: "No error body"
                rejectedResponse(responseCode, errorText)
            } else {
                val responseJson = connectionLocal.inputStream.bufferedReader().use { it.readText() }
                json.decodeFromString(
                    GraphQLResponse.serializer(dataSerializer),
                    responseJson
                ).copy(httpStatusCode = responseCode)
            }
        } catch (e: Exception) {
            // No status: the request failed before or while getting one, which carries no permanent
            // signal (see `ErrorRecoverability`).
            GraphQLResponse(
                data = null,
                errors = listOf(
                    GraphQLError(message = e.message.toString())
                )
            )
        } finally {
            connection?.disconnect()
        }

        logErrors(response)
        response
    }

    /**
     * Turns a non-2xx response into the errors-carrying envelope callers expect. A rejected request can
     * still return GraphQL errors, whose `extensions` are more specific than the status code, so they
     * are read from the body when it is one.
     */
    private fun <T> rejectedResponse(statusCode: Int, body: String): GraphQLResponse<T> {
        val errors = try {
            json.decodeFromString(GraphQLErrorEnvelope.serializer(), body).errors
        } catch (_: Exception) {
            null
        }?.takeIf { it.isNotEmpty() }
            ?: listOf(GraphQLError(message = "HTTP Error $statusCode: $body"))

        return GraphQLResponse(data = null, errors = errors, httpStatusCode = statusCode)
    }

    private fun logErrors(response: GraphQLResponse<*>) {
        val errors = response.errors?.takeIf { it.isNotEmpty() } ?: return
        errors.forEach { error ->
            logger.error("GraphQLClient error: ${error.message}")
            error.locations?.forEach { location ->
                logger.error("  at line ${location.line}, column ${location.column}")
            }
        }
    }
}
