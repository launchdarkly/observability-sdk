package com.launchdarkly.observability.network

import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.ByteArrayInputStream
import java.io.IOException
import java.net.HttpURLConnection

class GraphQLClientTest {

    @Serializable
    data class TestData(val id: String, val name: String)

    private val testQuery = """
        query EmptyQuery {
            fakeField
        }
    """.trimIndent()

    private lateinit var graphQLClient: GraphQLClient
    private lateinit var mockConnection: HttpURLConnection
    private var openConnectionWasCalled: Pair<Boolean, String> = Pair(false, "url")

    @BeforeEach
    fun setUp() {
        mockConnection = mockk<HttpURLConnection>(relaxed = true)
        every { mockConnection.responseCode } returns HttpURLConnection.HTTP_OK

        graphQLClient = GraphQLClient(
            endpoint = "https://api.example.com/graphql",
            headers = mapOf("Authorization" to "Bearer token123"),
            connectionProvider = object : UrlConnectionProvider {
                override fun openConnection(url: String): HttpURLConnection {
                    openConnectionWasCalled = (true to url)
                    return mockConnection
                }
            },
            logger = mockk(relaxed = true)
        )
    }

    @AfterEach
    fun tearDown() {
        openConnectionWasCalled = (false to "url")
        unmockkAll()
    }

    @Test
    fun `execute should make successful GraphQL request and return the data`() = runTest {
        respondWith("""{"data": {"id": "123", "name": "John Doe"}}""")

        val result = graphQLClient.execute(
            query = testQuery,
            variables = mapOf("test_variable" to JsonPrimitive("567")),
            dataSerializer = TestData.serializer()
        )

        assertEquals("123", result.id)
        assertEquals("John Doe", result.name)
        assertEquals(openConnectionWasCalled.first, true)
        assertEquals(openConnectionWasCalled.second, "https://api.example.com/graphql")
    }

    @Test
    fun `execute should throw the GraphQL errors in a response`() = runTest {
        respondWith(
            """{"data": null, "errors": [{"message": "User not found", "locations": [{"line": 1, "column": 2}], "path": ["user"]}]}"""
        )

        val failure = assertThrows<GraphQLClientException.GraphQLErrors> { execute() }

        assertEquals(1, failure.errors.size)
        assertEquals("User not found", failure.errors.first().message)
        assertEquals(1, failure.errors.first().locations?.first()?.line)
        assertEquals(2, failure.errors.first().locations?.first()?.column)
        assertEquals(listOf("user"), failure.errors.first().path)
        assertTrue(failure.message!!.contains("User not found"))
    }

    @Test
    fun `execute should read error extensions so failures can be classified`() = runTest {
        respondWith(
            """
            {"data": null, "errors": [{
                "message": "Session replay is not available in this region.",
                "extensions": {"code": "SESSION_REPLAY_BLOCKED_IN_REGION", "retryable": false}
            }]}
            """.trimIndent()
        )

        val failure = assertThrows<GraphQLClientException.GraphQLErrors> { execute() }

        assertEquals("SESSION_REPLAY_BLOCKED_IN_REGION", failure.errors.first().extensions?.code)
        assertEquals(false, failure.errors.first().extensions?.retryable)
    }

    @Test
    fun `execute should throw missing data when a response carries neither data nor errors`() = runTest {
        respondWith("""{"data": null}""")

        assertThrows<GraphQLClientException.MissingData> { execute() }
    }

    @Test
    fun `execute should throw a decoding failure for an unexpected response shape`() = runTest {
        respondWith("""{"data": {"unexpected": true}}""")

        assertThrows<GraphQLClientException.Decoding> { execute() }
    }

    @Test
    fun `execute should keep the status of an HTTP error response`() = runTest {
        val errorResponse = """{"error": "Unauthorized"}"""
        every { mockConnection.responseCode } returns HttpURLConnection.HTTP_UNAUTHORIZED
        every { mockConnection.errorStream } returns ByteArrayInputStream(errorResponse.toByteArray())

        val failure = assertThrows<GraphQLClientException.HttpStatus> { execute() }

        assertEquals(HttpURLConnection.HTTP_UNAUTHORIZED, failure.statusCode)
        assertEquals(errorResponse, failure.body)
        // Not a GraphQL envelope, so there is nothing more specific than the status.
        assertNull(failure.errors)
        assertEquals("HTTP Error ${HttpURLConnection.HTTP_UNAUTHORIZED}: $errorResponse", failure.message)
    }

    @Test
    fun `execute should keep the GraphQL errors a rejected response carries`() = runTest {
        every { mockConnection.responseCode } returns HttpURLConnection.HTTP_FORBIDDEN
        every { mockConnection.errorStream } returns ByteArrayInputStream(
            """{"errors": [{"message": "blocked", "extensions": {"retryable": true}}]}""".toByteArray()
        )

        val failure = assertThrows<GraphQLClientException.HttpStatus> { execute() }

        assertEquals(HttpURLConnection.HTTP_FORBIDDEN, failure.statusCode)
        assertEquals("blocked", failure.errors?.first()?.message)
        assertEquals(true, failure.errors?.first()?.extensions?.retryable)
    }

    @Test
    fun `execute should handle HTTP error with no error stream`() = runTest {
        every { mockConnection.responseCode } returns HttpURLConnection.HTTP_INTERNAL_ERROR
        every { mockConnection.errorStream } returns null

        val failure = assertThrows<GraphQLClientException.HttpStatus> { execute() }

        assertEquals(HttpURLConnection.HTTP_INTERNAL_ERROR, failure.statusCode)
        assertEquals("HTTP Error ${HttpURLConnection.HTTP_INTERNAL_ERROR}: No error body", failure.message)
    }

    @Test
    fun `execute should report an IOException during the request as a transport failure`() = runTest {
        every { mockConnection.outputStream } throws IOException("Connection failed")

        val failure = assertThrows<GraphQLClientException.Transport> { execute() }

        assertEquals("Connection failed", failure.cause?.message)
    }

    @Test
    fun `execute should set correct HTTP headers`() = runTest {
        respondWith("""{"data": {"id": "123", "name": "John Doe"}}""")

        execute()

        verify {
            mockConnection.requestMethod = "POST"
            mockConnection.setRequestProperty("Content-Type", "application/json")
            mockConnection.setRequestProperty("Authorization", "Bearer token123")
            mockConnection.doOutput = true
            mockConnection.connectTimeout = 10000
            mockConnection.readTimeout = 10000
        }
    }

    private fun respondWith(responseJson: String) {
        every { mockConnection.inputStream } returns ByteArrayInputStream(responseJson.toByteArray())
    }

    private suspend fun execute(): TestData =
        graphQLClient.execute(query = testQuery, dataSerializer = TestData.serializer())
}
