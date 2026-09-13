package com.launchdarkly.observability.network

import com.launchdarkly.observability.context.ObserveLogger
import com.launchdarkly.observability.coroutines.DispatcherProviderHolder
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.cancellation.CancellationException

@Serializable
data class GraphQLRequest(
    val query: String,
    val variables: Map<String, JsonElement> = emptyMap()
)

/** Standard GraphQL envelope: `{ data, errors }`. */
@Serializable
private data class GraphQLResponse<T>(
    val data: T?,
    val errors: List<GraphQLError>? = null
)

/**
 * Every way a [GraphQLClient.execute] call can fail, as the failure the caller can act on:
 * [ErrorRecoverability] classifies these cases directly, so no caller has to re-derive a status code or a
 * `retryable` flag from an error message.
 */
sealed class GraphQLClientException(message: String, cause: Throwable? = null) : RuntimeException(message, cause) {
    /**
     * The request was rejected with a non-2xx status. A rejected request can still return a GraphQL
     * envelope, in which case its [errors] are carried here too.
     */
    class HttpStatus(
        val statusCode: Int,
        val body: String,
        val errors: List<GraphQLError>? = null,
    ) : GraphQLClientException("HTTP Error $statusCode: $body")

    /** The response was accepted but carries `errors`, which is how the public graph reports rejections. */
    class GraphQLErrors(
        val errors: List<GraphQLError>,
    ) : GraphQLClientException("GraphQL errors: ${errors.joinToString(" | ") { it.message }}")

    /** The response carried neither `data` nor `errors`. */
    class MissingData : GraphQLClientException("Missing `data` in GraphQL response")

    /** The request never reached a status: connectivity loss, a timeout, or a request we failed to send. */
    class Transport(cause: Throwable) : GraphQLClientException("Transport error: ${cause.message}", cause)

    /** The response was not the shape the operation expects. */
    class Decoding(cause: Throwable) : GraphQLClientException("Decoding error: ${cause.message}", cause)
}

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
        private const val NO_ERROR_BODY = "No error body"
    }

    /**
     * Executes a GraphQL query
     * @param query The GraphQL query string
     * @param variables Query variables
     * @param dataSerializer Kotlinx serialization serializer for the expected response data type
     * @return the deserialized `data` of the response
     * @throws GraphQLClientException for every failure, including a GraphQL `errors` response
     */
    suspend fun <T> execute(
        query: String,
        variables: Map<String, JsonElement> = emptyMap(),
        dataSerializer: KSerializer<T>,
        compress: Boolean = true
    ): T = withContext(DispatcherProviderHolder.current.io) {
        var connection: HttpURLConnection? = null
        try {
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
                val body = connectionLocal.errorStream?.bufferedReader()?.use { it.readText() } ?: NO_ERROR_BODY
                throw GraphQLClientException.HttpStatus(responseCode, body, errorsIn(body))
            }

            val responseJson = connectionLocal.inputStream.bufferedReader().use { it.readText() }
            val envelope = try {
                json.decodeFromString(GraphQLResponse.serializer(dataSerializer), responseJson)
            } catch (e: Exception) {
                throw GraphQLClientException.Decoding(e)
            }

            envelope.errors?.takeIf { it.isNotEmpty() }?.let { throw GraphQLClientException.GraphQLErrors(it) }

            envelope.data ?: throw GraphQLClientException.MissingData()
        } catch (e: GraphQLClientException) {
            logFailure(e)
            throw e
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // The request never reached a status, which carries no permanent signal (see
            // `ErrorRecoverability`).
            throw GraphQLClientException.Transport(e).also { logFailure(it) }
        } finally {
            connection?.disconnect()
        }
    }

    /**
     * The GraphQL errors a rejected body carried, or `null` when it was not a GraphQL envelope. Their
     * `extensions` are more specific than the status code, so they are worth reading off a rejection.
     */
    private fun errorsIn(body: String): List<GraphQLError>? = try {
        json.decodeFromString(GraphQLErrorEnvelope.serializer(), body).errors?.takeIf { it.isNotEmpty() }
    } catch (_: Exception) {
        null
    }

    private fun logFailure(failure: GraphQLClientException) {
        logger.error("GraphQLClient error: ${failure.message}")
        val errors = when (failure) {
            is GraphQLClientException.GraphQLErrors -> failure.errors
            is GraphQLClientException.HttpStatus -> failure.errors
            else -> null
        }
        errors?.forEach { error ->
            error.locations?.forEach { location ->
                logger.error("  at line ${location.line}, column ${location.column}")
            }
        }
    }
}
