package com.launchdarkly.observability.network

import com.launchdarkly.observability.context.ObserveLogger
import com.launchdarkly.observability.coroutines.DispatcherProviderHolder
import com.launchdarkly.observability.json.JsonByteWriter
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.cancellation.CancellationException

/**
 * @property variables Written by [JsonByteWriter.anyValue], so each value may be `null`, a [String],
 * [Boolean] or [Number], or a nested [Map] or [List] of those.
 */
data class GraphQLRequest(
    val query: String,
    val variables: Map<String, Any?> = emptyMap()
) {
    internal fun toJsonBytes(): ByteArray = JsonByteWriter.encode {
        beginObject()
        name("query").value(query)
        // No variables is the default, and defaults are not written.
        if (variables.isNotEmpty()) {
            name("variables")
            anyValue(variables)
        }
        endObject()
    }
}

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

data class GraphQLError(
    val message: String,
    val locations: List<GraphQLLocation>? = null,
    val path: List<String>? = null,
    val extensions: GraphQLErrorExtensions? = null
) {
    internal companion object {
        fun fromJson(json: JSONObject) = GraphQLError(
            message = json.getString("message"),
            locations = json.objectListOrNull("locations", GraphQLLocation::fromJson)?.filterNotNull(),
            // GraphQL paths mix field names and list indices; both are kept as their string form.
            path = json.listOrNull("path") { array, index -> array.get(index).toString() },
            extensions = json.objectOrNull("extensions", GraphQLErrorExtensions::fromJson)
        )

        /** The `errors` of a GraphQL envelope, or `null` when it has none. */
        fun listFromEnvelope(envelope: JSONObject): List<GraphQLError>? =
            envelope.objectListOrNull("errors", ::fromJson)?.filterNotNull()?.takeIf { it.isNotEmpty() }
    }
}

/**
 * Server-supplied metadata about a [GraphQLError].
 */
data class GraphQLErrorExtensions(
    /** Machine-readable error identifier, e.g. `SESSION_REPLAY_BLOCKED_IN_REGION`. */
    val code: String? = null,
    /**
     * The server's own verdict on whether retrying the operation can succeed. Takes precedence over
     * any status-code based classification when present.
     */
    val retryable: Boolean? = null
) {
    internal companion object {
        fun fromJson(json: JSONObject) = GraphQLErrorExtensions(
            code = json.stringOrNull("code"),
            retryable = json.booleanOrNull("retryable")
        )
    }
}

data class GraphQLLocation(
    val line: Int,
    val column: Int
) {
    internal companion object {
        fun fromJson(json: JSONObject) = GraphQLLocation(
            line = json.getInt("line"),
            column = json.getInt("column")
        )
    }
}

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
     * @param dataParser Builds the expected response data type from the response's `data` object.
     * It should throw when the object is not the shape the operation expects.
     * @return the parsed `data` of the response
     * @throws GraphQLClientException for every failure, including a GraphQL `errors` response
     */
    suspend fun <T> execute(
        query: String,
        variables: Map<String, Any?> = emptyMap(),
        dataParser: (JSONObject) -> T,
        compress: Boolean = true
    ): T = withContext(DispatcherProviderHolder.current.io) {
        var connection: HttpURLConnection? = null
        try {
            val request = GraphQLRequest(
                query = query,
                variables = variables
            )

            val requestBytes = request.toJsonBytes()
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
            val (errors, data) = try {
                val envelope = JSONObject(responseJson)
                GraphQLError.listFromEnvelope(envelope) to envelope.objectOrNull("data")
            } catch (e: Exception) {
                throw GraphQLClientException.Decoding(e)
            }

            errors?.let { throw GraphQLClientException.GraphQLErrors(it) }

            data ?: throw GraphQLClientException.MissingData()

            try {
                dataParser(data)
            } catch (e: Exception) {
                throw GraphQLClientException.Decoding(e)
            }
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
        GraphQLError.listFromEnvelope(JSONObject(body))
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
