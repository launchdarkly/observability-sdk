package com.launchdarkly.observability.network

import com.launchdarkly.logging.LDLogger
import com.launchdarkly.observability.coroutines.DispatcherProviderHolder
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import java.io.IOException
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.GZIPOutputStream

@Serializable
data class GraphQLRequest(
    val query: String,
    val variables: Map<String, JsonElement> = emptyMap()
)

@Serializable
data class GraphQLResponse<T>(
    val data: T?,
    val errors: List<GraphQLError>? = null
)

@Serializable
data class GraphQLError(
    val message: String,
    val locations: List<GraphQLLocation>? = null,
    val path: List<String>? = null
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
    private val logger: LDLogger,
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
     * @param queryFileName The .graphql file name
     * @param variables Query variables
     * @param dataSerializer Kotlinx serialization serializer for the expected response data type
     * @return GraphQLResponse containing either the deserialized data or error information
     */
    suspend fun <T> execute(
        queryFileName: String,
        variables: Map<String, JsonElement> = emptyMap(),
        dataSerializer: KSerializer<T>,
        compress: Boolean = true
    ): GraphQLResponse<T> = withContext(DispatcherProviderHolder.current.io) {
        var connection: HttpURLConnection? = null
        val response: GraphQLResponse<T> = try {
            val query = loadQuery(queryFileName)
            val request = GraphQLRequest(
                query = query,
                variables = variables
            )

            val requestJson = json.encodeToString(request)
            val requestBytes = requestJson.toByteArray(Charsets.UTF_8)
            val payloadBytes = if (compress) gzip(requestBytes) else requestBytes
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
            val responseJson = if (responseCode == HttpURLConnection.HTTP_OK) {
                connectionLocal.inputStream.bufferedReader().use { it.readText() }
            } else {
                val errorText = connectionLocal.errorStream?.bufferedReader()?.use { it.readText() } ?: "No error body"
                throw IOException("HTTP Error $responseCode: $errorText")
            }

            json.decodeFromString(
                GraphQLResponse.serializer(dataSerializer),
                responseJson
            )
        } catch (e: Exception) {
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
     * Loads GraphQL query from resources
     * @param queryFilepath The .graphql file path
     * @return Query string
     */
    private fun loadQuery(queryFilepath: String): String {
        return this::class.java.classLoader?.getResourceAsStream(queryFilepath)?.bufferedReader()?.use {
            it.readText()
        } ?: throw IllegalStateException("Could not load GraphQL query file: $queryFilepath")
    }

    private fun gzip(data: ByteArray): ByteArray {
        val byteStream = ByteArrayOutputStream()
        GZIPOutputStream(byteStream).use { gzipStream ->
            gzipStream.write(data)
        }
        return byteStream.toByteArray()
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
