package com.example.androidobservability

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.launchdarkly.observability.interfaces.Metric
import com.launchdarkly.observability.sdk.LDObserve
import com.launchdarkly.sdk.ContextKind
import com.launchdarkly.sdk.LDContext
import com.launchdarkly.sdk.android.LDClient
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.logs.Severity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedInputStream
import java.net.HttpURLConnection
import java.net.URL

class ViewModel : ViewModel() {

    fun triggerMetric() {
        LDObserve.recordMetric(Metric("test", 50.0))
    }

    fun triggerError() {
        LDObserve.recordError(
            Error("Manual error womp womp", Error("The error that caused the other error.")),
            Attributes.of(AttributeKey.stringKey("FakeAttribute"), "FakeVal")
        )
    }

    fun triggerLog() {
        LDObserve.recordLog(
            "Test Log",
            Severity.DEBUG,
            Attributes.of(AttributeKey.stringKey("FakeAttribute"), "FakeVal")
        )
    }

    fun triggerCustomLog(
        message: String,
        severity: Severity = Severity.INFO,
        attributes: Attributes = Attributes.empty()
    ) {
        if (message.isNotEmpty()) {
            LDObserve.recordLog(
                message = message,
                severity = severity,
                attributes = attributes
            )
        }
    }

    fun triggerCustomSpan(spanName: String) {
        if (spanName.isNotEmpty()) {
            viewModelScope.launch(Dispatchers.IO) {
                val customSpan = LDObserve.startSpan(
                    name = spanName,
                    attributes = Attributes.of(
                        AttributeKey.stringKey("custom_span"), "true"
                    )
                )
                customSpan.end()
            }
        }
    }

    fun triggerNestedSpans() {
        viewModelScope.launch(Dispatchers.IO) {
            val newSpan0 = LDObserve.startSpan("FakeSpan", Attributes.empty())
            newSpan0.makeCurrent().use {
                val newSpan1 = LDObserve.startSpan("FakeSpan1", Attributes.empty())
                newSpan1.makeCurrent().use {
                    val newSpan2 = LDObserve.startSpan("FakeSpan2", Attributes.empty())
                    newSpan2.makeCurrent().use {
                        sendOkHttpRequest()
                        sendURLRequest()
                        newSpan2.end()
                    }
                    newSpan1.end()
                }
                newSpan0.end()
            }
        }
    }

    fun triggerCrash() {
        throw RuntimeException("Failed to connect to bogus server.")
    }

    fun triggerHttpRequests() {
        viewModelScope.launch(Dispatchers.IO) {
            sendOkHttpRequest()
            sendURLRequest()
        }
    }

    fun identifyLDContext(contextKey: String = "test-context-key") {
        val context = LDContext.builder(ContextKind.DEFAULT, contextKey)
            .name("test-context-name")
            .build()

        LDClient.get().identify(context)
    }

    private fun sendOkHttpRequest() {
        // Create HTTP client
        val client = OkHttpClient()

        // Build request
        val request: Request = Request.Builder()
            .url("https://www.google.com")
            .build()

        client.newCall(request).execute().use { response ->
            println("Response code: " + response.code)
            println("Response body: " + response.body?.string())
        }
    }

    private fun sendURLRequest() {
        val url = URL("https://www.android.com/")
        val urlConnection = url.openConnection() as HttpURLConnection
        try {
            val output = BufferedInputStream(urlConnection.inputStream).bufferedReader().use { it.readText() }
            println("URLRequest output: $output")
        } finally {
            urlConnection.disconnect()
        }
    }
}
