package com.launchdarkly.observability.graph

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.Serializable
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

@Serializable
data class GraphQLRequest(
    val query: String,
    val variables: Map<String, String> = emptyMap()
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

/**
 * Generic GraphQL client for making HTTP requests to GraphQL endpoints
 */
class GraphQLClient(
    val endpoint: String,
    val headers: Map<String, String> = emptyMap(),
    internal val json: Json = Json { ignoreUnknownKeys = true }
) {

    companion object {
        internal const val CONNECT_TIMEOUT = 10000
        internal const val READ_TIMEOUT = 10000
    }

    /**
     * Executes a GraphQL query
     * @param queryFileName The .graphql file name
     * @param variables Query variables
     * @return GraphQLResponse with data or errors
     */
    internal suspend inline fun <reified T> execute(
        queryFileName: String,
        variables: Map<String, String> = emptyMap()
    ): GraphQLResponse<T> = withContext(Dispatchers.IO) {
        try {
            val query = loadQuery(queryFileName)
            val request = GraphQLRequest(
                query = query,
                variables = variables
            )

            val requestJson = json.encodeToString(request)
            val url = URL(endpoint)
            val connection = url.openConnection() as HttpURLConnection

            connection.apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Content-Length", requestJson.toByteArray().size.toString())

                // Add custom headers
                headers.forEach { (key, value) ->
                    setRequestProperty(key, value)
                }

                doOutput = true
                connectTimeout = CONNECT_TIMEOUT
                readTimeout = READ_TIMEOUT
            }

            // Send request
            connection.outputStream.use { outputStream ->
                outputStream.write(requestJson.toByteArray())
                outputStream.flush()
            }

            // Read response
            val responseCode = connection.responseCode
            val responseJson = if (responseCode == HttpURLConnection.HTTP_OK) {
                connection.inputStream.bufferedReader().use { it.readText() }
            } else {
                connection.errorStream?.bufferedReader()?.use { it.readText() }
                    ?: throw IOException("HTTP Error $responseCode with no error body")
            }

            json.decodeFromString<GraphQLResponse<T>>(responseJson)

        } catch (e: IOException) {
            GraphQLResponse(
                data = null,
                errors = listOf(
                    GraphQLError(message = "Network error: ${e.message}")
                )
            )
        } catch (e: Exception) {
            GraphQLResponse(
                data = null,
                errors = listOf(
                    GraphQLError(message = e.message.toString())
                )
            )
        }
    }

    /**
     * Loads GraphQL query from resources
     * @param queryFileName The .graphql file name
     * @return Query string
     */
    private fun loadQuery(queryFileName: String): String {
        val resourcePath = "graphql/$queryFileName"
        return this::class.java.classLoader?.getResourceAsStream(resourcePath)?.bufferedReader()?.use {
            it.readText()
        } ?: throw IllegalStateException("Could not load GraphQL query file: $resourcePath")
    }

    /**
     * Utility method to print GraphQL errors
     */
    fun <T> printErrors(response: GraphQLResponse<T>) {
        response.errors?.forEach { error ->
            println("GraphQL Error: ${error.message}")
            error.locations?.forEach { location ->
                println("  at line ${location.line}, column ${location.column}")
            }
        }
    }
}
