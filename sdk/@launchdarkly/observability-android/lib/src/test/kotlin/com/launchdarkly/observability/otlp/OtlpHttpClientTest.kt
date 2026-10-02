package com.launchdarkly.observability.otlp

import com.launchdarkly.observability.network.UrlConnectionProvider
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.util.zip.GZIPInputStream
import kotlin.time.Duration.Companion.seconds

class OtlpHttpClientTest {

    private fun body(json: String): ByteArray = json.toByteArray(Charsets.UTF_8)

    private lateinit var mockConnection: HttpURLConnection
    private lateinit var capturedBody: ByteArrayOutputStream
    private var openConnectionCalledWith: String? = null

    @BeforeEach
    fun setUp() {
        mockConnection = mockk<HttpURLConnection>(relaxed = true)
        capturedBody = ByteArrayOutputStream()
        every { mockConnection.responseCode } returns HttpURLConnection.HTTP_OK
        every { mockConnection.outputStream } returns capturedBody
        every { mockConnection.inputStream } returns ByteArrayInputStream(ByteArray(0))
    }

    @AfterEach
    fun tearDown() {
        openConnectionCalledWith = null
        unmockkAll()
    }

    private fun clientWith(
        config: OtlpConfiguration = OtlpConfiguration(),
        endpoint: String = "https://otlp.example.com/v1/logs",
    ): OtlpHttpClient {
        val provider = object : UrlConnectionProvider {
            override fun openConnection(url: String): HttpURLConnection {
                openConnectionCalledWith = url
                return mockConnection
            }
        }
        return OtlpHttpClient(
            endpoint = endpoint,
            config = config,
            connectionProvider = provider,
        )
    }

    @Test
    fun `send gzips body and sets the expected headers`() = runTest {
        val client = clientWith()

        client.send(body = body("""{"a":"hi","b":7}"""))

        assertEquals("https://otlp.example.com/v1/logs", openConnectionCalledWith)
        verify {
            mockConnection.requestMethod = "POST"
            mockConnection.doOutput = true
            mockConnection.setRequestProperty("Content-Type", "application/json")
            mockConnection.setRequestProperty("Content-Encoding", "gzip")
            mockConnection.setRequestProperty("User-Agent", match { it.startsWith("OTel-OTLP-Exporter-Kotlin/") })
        }

        val raw = GZIPInputStream(ByteArrayInputStream(capturedBody.toByteArray()))
            .use { it.readBytes().toString(Charsets.UTF_8) }
        assertEquals("""{"a":"hi","b":7}""", raw)
    }

    @Test
    fun `send without gzip sends the raw body and omits Content-Encoding`() = runTest {
        val client = clientWith(OtlpConfiguration(compression = OtlpCompression.NONE))

        client.send(body = body("""{"a":"ok","b":1}"""))

        assertEquals("""{"a":"ok","b":1}""", capturedBody.toByteArray().toString(Charsets.UTF_8))
        verify(exactly = 0) { mockConnection.setRequestProperty("Content-Encoding", any()) }
    }

    @Test
    fun `send attaches custom headers from configuration`() = runTest {
        val client = clientWith(
            OtlpConfiguration(
                compression = OtlpCompression.NONE,
                headers = mapOf("X-Api-Key" to "sdk-token", "X-Project" to "abc"),
            )
        )

        client.send(body("{}"))

        verify {
            mockConnection.setRequestProperty("X-Api-Key", "sdk-token")
            mockConnection.setRequestProperty("X-Project", "abc")
        }
    }

    @Test
    fun `send applies the smaller of the explicit and configured timeouts`() = runTest {
        val client = clientWith(OtlpConfiguration(timeout = 30.seconds))

        client.send(
            body = body("{}"),
            explicitTimeout = 5.seconds,
        )

        verify {
            mockConnection.connectTimeout = 5_000
            mockConnection.readTimeout = 5_000
        }
    }

    @Test
    fun `send throws IOException when status is not 2xx`() {
        every { mockConnection.responseCode } returns 503
        every { mockConnection.errorStream } returns ByteArrayInputStream("oops".toByteArray())
        val client = clientWith()

        val error = assertThrows(IOException::class.java) {
            runTest { client.send(body("{}")) }
        }
        assertTrue(error.message!!.contains("503"), "message should include status: ${error.message}")
        assertTrue(error.message!!.contains("oops"), "message should include body: ${error.message}")
    }

    @Test
    fun `send always disconnects the connection`() = runTest {
        every { mockConnection.responseCode } returns 500
        every { mockConnection.errorStream } returns null
        val client = clientWith()

        runCatching { client.send(body("{}")) }

        verify { mockConnection.disconnect() }
    }
}
