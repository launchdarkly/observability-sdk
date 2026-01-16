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
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNotNull
import org.junit.jupiter.api.assertNull
import java.io.ByteArrayInputStream
import java.io.IOException
import java.net.HttpURLConnection

class GraphQLClientTest {

    @Serializable
    data class TestData(val id: String, val name: String)

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
    fun `execute should handle missing GraphQL query file`() = runTest {
        val result = graphQLClient.execute(
            queryFileName = "nonexistent-query.graphql",
            dataSerializer = TestData.serializer()
        )

        assertNull(result.data)
        assertNotNull(result.errors)
        assertEquals(1, result.errors.size)
        assertTrue(result.errors.first().message.contains("Could not load GraphQL query file"))
    }

    @Test
    fun `execute should make successful GraphQL request and return a valid response`() = runTest {
        val responseJson = """{"data": {"id": "123", "name": "John Doe"}}"""

        every { mockConnection.inputStream } returns ByteArrayInputStream(responseJson.toByteArray())

        val result = graphQLClient.execute(
            queryFileName = "test-query.graphql",
            variables = mapOf("test_variable" to JsonPrimitive("567")),
            dataSerializer = TestData.serializer()
        )

        assertNull(result.errors)
        assertNotNull(result.data)
        assertEquals("123", result.data.id)
        assertEquals("John Doe", result.data.name)
        assertEquals(openConnectionWasCalled.first, true)
        assertEquals(openConnectionWasCalled.second, "https://api.example.com/graphql")
    }

    @Test
    fun `execute should handle GraphQL errors in response`() = runTest {
        val responseJson = """{"data": null, "errors": [{"message": "User not found", "locations": [{"line": 1, "column": 2}], "path": ["user"]}]}"""

        every { mockConnection.inputStream } returns ByteArrayInputStream(responseJson.toByteArray())

        val result = graphQLClient.execute(
            queryFileName = "test-query.graphql",
            dataSerializer = TestData.serializer()
        )

        assertNull(result.data)
        assertNotNull(result.errors)
        assertEquals(1, result.errors.size)
        assertEquals("User not found", result.errors.first().message)
        assertEquals(1, result.errors.first().locations?.first()?.line)
        assertEquals(2, result.errors.first().locations?.first()?.column)
        assertEquals(listOf("user"), result.errors.first().path)
    }

    @Test
    fun `execute should handle HTTP error responses`() = runTest {
        val errorResponse = """{"error": "Unauthorized"}"""

        every { mockConnection.responseCode } returns HttpURLConnection.HTTP_UNAUTHORIZED
        every { mockConnection.errorStream } returns ByteArrayInputStream(errorResponse.toByteArray())

        val result = graphQLClient.execute(
            queryFileName = "test-query.graphql",
            dataSerializer = TestData.serializer()
        )

        assertNull(result.data)
        assertNotNull(result.errors)
        assertEquals("HTTP Error ${HttpURLConnection.HTTP_UNAUTHORIZED}: $errorResponse", result.errors.first().message)
    }

    @Test
    fun `execute should handle HTTP error with no error stream`() = runTest {
        every { mockConnection.responseCode } returns HttpURLConnection.HTTP_INTERNAL_ERROR
        every { mockConnection.errorStream } returns null

        val result = graphQLClient.execute(
            queryFileName = "test-query.graphql",
            dataSerializer = TestData.serializer()
        )

        assertNull(result.data)
        assertNotNull(result.errors)
        assertEquals(1, result.errors.size)
        assertEquals("HTTP Error ${HttpURLConnection.HTTP_INTERNAL_ERROR}: No error body", result.errors.first().message)
    }

    @Test
    fun `execute should handle IOException during request`() = runTest {
        every { mockConnection.outputStream } throws IOException("Connection failed")

        val result = graphQLClient.execute(
            queryFileName = "test-query.graphql",
            dataSerializer = TestData.serializer()
        )

        assertNull(result.data)
        assertNotNull(result.errors)
        assertEquals(1, result.errors.size)
        assertEquals("Connection failed", result.errors.first().message)
    }

    @Test
    fun `execute should set correct HTTP headers`() = runTest {
        val responseJson = """{"data": {"id": "123", "name": "John Doe"}}"""

        every { mockConnection.inputStream } returns ByteArrayInputStream(responseJson.toByteArray())

        graphQLClient.execute(
            queryFileName = "test-query.graphql",
            dataSerializer = TestData.serializer()
        )

        verify {
            mockConnection.requestMethod = "POST"
            mockConnection.setRequestProperty("Content-Type", "application/json")
            mockConnection.setRequestProperty("Authorization", "Bearer token123")
            mockConnection.doOutput = true
            mockConnection.connectTimeout = 10000
            mockConnection.readTimeout = 10000
        }
    }
}
