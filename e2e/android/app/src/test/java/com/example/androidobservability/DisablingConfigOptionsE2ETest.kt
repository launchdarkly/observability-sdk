package com.example.androidobservability

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.example.androidobservability.TestUtils.TelemetryType
import com.example.androidobservability.TestUtils.waitForTelemetryData
import com.launchdarkly.observability.api.ObservabilityOptions
import com.launchdarkly.observability.interfaces.Metric
import com.launchdarkly.observability.sdk.LDObserve
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.logs.Severity
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertNotNull
import junit.framework.TestCase.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(application = TestApplication::class)
class DisablingConfigOptionsE2ETest {

    private val application = ApplicationProvider.getApplicationContext<Application>() as TestApplication

    @Test
    fun `Logs should NOT be exported when logsApiLevel is NONE`() {
        application.observabilityOptions = getOptionsAllEnabled().copy(logsApiLevel = ObservabilityOptions.LogLevel.NONE)
        application.initForTest()
        val logsUrl = "http://localhost:${application.mockWebServer?.port}/v1/logs"

        triggerTestLog(severity = Severity.TRACE)
        LDObserve.flush()
        waitForTelemetryData(telemetryInspector = application.telemetryInspector, telemetryType = TelemetryType.LOGS)
        val logsExported = application.telemetryInspector?.logExporter?.finishedLogRecordItems

        assertTrue(logsExported?.isEmpty() == true)
        assertFalse(requestsContainsUrl(logsUrl))
    }

    @Test
    fun `Logs should NOT be exported when log severity is lower than logsApiLevel`() {
        application.observabilityOptions = getOptionsAllEnabled().copy(logsApiLevel = ObservabilityOptions.LogLevel.INFO)
        application.initForTest()
        val logsUrl = "http://localhost:${application.mockWebServer?.port}/v1/logs"

        triggerTestLog(severity = Severity.TRACE)
        LDObserve.flush()
        waitForTelemetryData(telemetryInspector = application.telemetryInspector, telemetryType = TelemetryType.LOGS)
        val logsExported = application.telemetryInspector?.logExporter?.finishedLogRecordItems

        assertTrue(logsExported?.isEmpty() == true)
        assertFalse(requestsContainsUrl(logsUrl))
    }

    @Test
    fun `Logs should be exported when log severity is higher than logsApiLevel`() {
        application.observabilityOptions = getOptionsAllEnabled().copy(logsApiLevel = ObservabilityOptions.LogLevel.INFO)
        application.initForTest()
        val logsUrl = "http://localhost:${application.mockWebServer?.port}/v1/logs"

        triggerTestLog(severity = Severity.WARN)
        LDObserve.flush()
        waitForTelemetryData(telemetryInspector = application.telemetryInspector, telemetryType = TelemetryType.LOGS)

        assertNotNull(application.telemetryInspector?.logExporter)
        assertTrue(requestsContainsUrl(logsUrl))
    }


    @Test
    fun `Spans should NOT be exported when TracesApi is disabled`() {
        application.observabilityOptions = getOptionsAllEnabled().copy(tracesApi = ObservabilityOptions.TracesApi.disabled())
        application.initForTest()
        val tracesUrl = "http://localhost:${application.mockWebServer?.port}/v1/traces"

        triggerTestSpan()
        LDObserve.flush()

        waitForTelemetryData(telemetryInspector = application.telemetryInspector, telemetryType = TelemetryType.SPANS)
        val spansExported = application.telemetryInspector?.spanExporter?.finishedSpanItems

        assertTrue(spansExported?.isEmpty() == true)
        assertFalse(requestsContainsUrl(tracesUrl))
    }

    @Test
    fun `Spans should NOT be exported when TracesApi does not include spans`() {
        application.observabilityOptions = getOptionsAllEnabled().copy(
            tracesApi = ObservabilityOptions.TracesApi(includeSpans = false)
        )
        application.initForTest()
        val tracesUrl = "http://localhost:${application.mockWebServer?.port}/v1/traces"

        triggerTestSpan()
        LDObserve.flush()

        waitForTelemetryData(telemetryInspector = application.telemetryInspector, telemetryType = TelemetryType.SPANS)
        val spansExported = application.telemetryInspector?.spanExporter?.finishedSpanItems

        assertTrue(spansExported?.isEmpty() == true)
        assertFalse(requestsContainsUrl(tracesUrl))
    }

    @Test
    fun `Spans should be exported when TracesApi is enabled`() {
        application.observabilityOptions = getOptionsAllEnabled().copy(tracesApi = ObservabilityOptions.TracesApi.enabled())
        application.initForTest()
        val tracesUrl = "http://localhost:${application.mockWebServer?.port}/v1/traces"

        triggerTestSpan()
        LDObserve.flush()
        waitForTelemetryData(telemetryInspector = application.telemetryInspector, telemetryType = TelemetryType.SPANS)

        assertNotNull(application.telemetryInspector?.spanExporter)
        assertTrue(requestsContainsUrl(tracesUrl))
    }

    @Test
    fun `Metrics should NOT be exported when disabled`() {
        application.observabilityOptions =
            getOptionsAllEnabled().copy(metricsApi = ObservabilityOptions.MetricsApi.disabled())
        application.initForTest()
        val metricsUrl = "http://localhost:${application.mockWebServer?.port}/v1/metrics"

        triggerTestMetric()
        LDObserve.flush()
        waitForTelemetryData(telemetryInspector = application.telemetryInspector, telemetryType = TelemetryType.METRICS)

        assertFalse(requestsContainsUrl(metricsUrl))
    }

    @Test
    fun `Metrics should be exported when enabled`() {
        application.observabilityOptions =
            getOptionsAllEnabled().copy(metricsApi = ObservabilityOptions.MetricsApi.enabled())
        application.initForTest()
        val metricsUrl = "http://localhost:${application.mockWebServer?.port}/v1/metrics"

        triggerTestMetric()
        LDObserve.flush()
        waitForTelemetryData(telemetryInspector = application.telemetryInspector, telemetryType = TelemetryType.METRICS)

        assertTrue(requestsContainsUrl(metricsUrl))
    }

    @Test
    fun `Errors should NOT be exported when TracesApi does not include errors`() {
        application.observabilityOptions = getOptionsAllEnabled().copy(
            tracesApi = ObservabilityOptions.TracesApi(includeErrors = false)
        )
        application.initForTest()
        val tracesUrl = "http://localhost:${application.mockWebServer?.port}/v1/traces"

        triggerError()
        LDObserve.flush()
        waitForTelemetryData(telemetryInspector = application.telemetryInspector, telemetryType = TelemetryType.SPANS)

        val spansExported = application.telemetryInspector?.spanExporter?.finishedSpanItems

        assertFalse(requestsContainsUrl(tracesUrl))
        assertEquals(0, spansExported?.size)
    }

    @Test
    fun `Errors should be exported as spans when TracesApi include errors but not spans`() {
        application.observabilityOptions = getOptionsAllEnabled().copy(
            tracesApi = ObservabilityOptions.TracesApi(includeErrors = true, includeSpans = false)
        )
        application.initForTest()
        val tracesUrl = "http://localhost:${application.mockWebServer?.port}/v1/traces"

        triggerError()
        LDObserve.flush()
        waitForTelemetryData(telemetryInspector = application.telemetryInspector, telemetryType = TelemetryType.SPANS)

        val spansExported = application.telemetryInspector?.spanExporter?.finishedSpanItems

        assertTrue(requestsContainsUrl(tracesUrl))
        assertEquals("highlight.error", spansExported?.get(0)?.name)
        assertEquals(
            "Test error",
            spansExported?.get(0)?.events?.get(0)?.attributes?.get(AttributeKey.stringKey("exception.message"))
        )
    }

    @Test
    fun `Crashes should NOT be exported when crashReporting instrumentation is disabled`() {
        application.observabilityOptions = getOptionsAllEnabled().copy(
            instrumentations = ObservabilityOptions.Instrumentations(crashReporting = false)
        )
        application.initForTest()
        val logsUrl = "http://localhost:${application.mockWebServer?.port}/v1/logs"

        Thread { throw RuntimeException("Exception for testing") }.start()

        waitForTelemetryData(telemetryInspector = application.telemetryInspector, telemetryType = TelemetryType.LOGS)
        val logsExported = application.telemetryInspector?.logExporter?.finishedLogRecordItems

        assertFalse(requestsContainsUrl(logsUrl))
        assertEquals(0, logsExported?.size)
    }

    @Test
    fun `Crashes should be exported as logs when crashReporting instrumentation is enabled logsApiLevel is NONE`() {
        application.observabilityOptions = getOptionsAllEnabled().copy(
            logsApiLevel = ObservabilityOptions.LogLevel.NONE,
            instrumentations = ObservabilityOptions.Instrumentations(crashReporting = true)
        )
        application.initForTest()
        val logsUrl = "http://localhost:${application.mockWebServer?.port}/v1/logs"
        val exceptionMessage = "Exception for testing"

        Thread { throw RuntimeException(exceptionMessage) }.start()

        waitForTelemetryData(telemetryInspector = application.telemetryInspector, telemetryType = TelemetryType.LOGS)
        val logsExported = application.telemetryInspector?.logExporter?.finishedLogRecordItems

        assertTrue(requestsContainsUrl(logsUrl))
        assertEquals(1, logsExported?.size)
        assertEquals(
            exceptionMessage,
            logsExported?.get(0)?.attributes?.get(AttributeKey.stringKey("exception.message"))
        )
    }

    private fun requestsContainsUrl(url: String): Boolean {
        while (true) {
            val request = application.mockWebServer?.takeRequest(100, TimeUnit.MILLISECONDS) ?: return false
            if (request.requestUrl.toString() == url) return true
        }
    }

    private fun triggerTestLog(severity: Severity = Severity.INFO) {
        LDObserve.recordLog(
            message = "test-log",
            severity = severity,
            attributes = Attributes.empty()
        )
    }

    private fun triggerTestSpan() {
        val span0 = LDObserve.startSpan(
            name = "test-span",
            attributes = Attributes.empty()
        )
        span0.end()
    }

    private fun triggerError() {
        LDObserve.recordError(
            Error("Test error"),
            Attributes.of(AttributeKey.stringKey("test"), "crash_test")
        )
    }

    private fun triggerTestMetric() {
        LDObserve.recordMetric(Metric("test", 50.0))
    }

    private fun getOptionsAllEnabled(): ObservabilityOptions {
        return ObservabilityOptions(
            debug = true,
            logsApiLevel = ObservabilityOptions.LogLevel.TRACE,
            tracesApi = ObservabilityOptions.TracesApi.enabled(),
            metricsApi = ObservabilityOptions.MetricsApi.enabled(),
            instrumentations = ObservabilityOptions.Instrumentations(
                crashReporting = true,
                activityLifecycle = true,
                launchTime = true
            )
        )
    }
}
