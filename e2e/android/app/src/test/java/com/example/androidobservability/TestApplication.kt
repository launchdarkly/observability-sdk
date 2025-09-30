package com.example.androidobservability

import com.launchdarkly.sdk.android.LDClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import java.net.InetAddress

class TestApplication : BaseApplication() {

    private val host = "127.0.0.1"
    var mockWebServer: MockWebServer? = null

    override fun onCreate() {
        // The Application class won't be initialized unless initForTest() is executed. This helps us to set up
        // everything we need in a test before calling super.onCreate().
    }

    private fun setupMockServer() {
        val responseBody = getSamplingConfigResponseBody()
        val response = MockResponse()
            .setResponseCode(200)
            .setBody(responseBody)
            .setHeader("Content-Type", "application/json")

        mockWebServer = MockWebServer().apply {
            enqueue(response)
            start(InetAddress.getByName(host), 0)
        }

        testUrl = "http://$host:${mockWebServer?.port}"
    }

    private fun getSamplingConfigResponseBody(): String {
        return assets
            .open("get_sampling_config_response.json")
            .bufferedReader()
            .use { it.readText() }
    }

    fun initForTest() {
        setupMockServer()
        super.realInit()
    }

    override fun onTerminate() {
        mockWebServer?.let {
            it.shutdown()
            mockWebServer = null
        }
        LDClient.get().close()
        super.onTerminate()
    }
}
