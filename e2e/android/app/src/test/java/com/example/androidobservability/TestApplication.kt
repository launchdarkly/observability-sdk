package com.example.androidobservability

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import java.net.InetAddress

class TestApplication : BaseApplication() {

    private val host = "127.0.0.1"
    private var mockWebServer: MockWebServer? = null

    override fun onCreate() {
        setupMockServer()
        super.onCreate()
    }

    private fun setupMockServer() {
        val responseBody = getSamplingConfigResponseBody()
        mockWebServer = MockWebServer().apply {
            enqueue(
                response = MockResponse()
                    .setResponseCode(200)
                    .setBody(responseBody)
                    .setHeader("Content-Type", "application/json")
            )
            start(InetAddress.getByName(host), 0)
        }

        val baseUrl = "http://$host:${mockWebServer?.port}/"
        System.setProperty("e2e_test_base_url", baseUrl)
    }

    private fun getSamplingConfigResponseBody(): String {
        return assets
            .open("get_sampling_config_response.json")
            .bufferedReader()
            .use { it.readText() }
    }

    override fun onTerminate() {
        mockWebServer?.let {
            it.shutdown()
            mockWebServer = null
        }
        super.onTerminate()
    }
}
