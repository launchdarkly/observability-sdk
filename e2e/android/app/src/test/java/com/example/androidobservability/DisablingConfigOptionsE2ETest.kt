package com.example.androidobservability

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.example.androidobservability.TestUtils.waitForTelemetryData
import com.launchdarkly.observability.interfaces.Metric
import com.launchdarkly.observability.sdk.LDObserve
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.logs.Severity
import com.example.androidobservability.TestUtils.TelemetryType
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertNotNull
import junit.framework.TestCase.assertNull
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
    fun `Logs should not be exported when disableLogs is set to true`() {
        application.pluginOptions = application.pluginOptions.copy(disableLogs = true)
        application.initForTest()
        val logsUrl = "http://localhost:${application.mockWebServer?.port}/v1/logs"

        triggerTestLog()
        LDObserve.flush()
        waitForTelemetryData(telemetryInspector = application.telemetryInspector, telemetryType = TelemetryType.LOGS)

        assertNull(application.telemetryInspector?.logExporter)
        assertFalse(requestsContainsUrl(logsUrl))
    }

    @Test
    fun `Logs should be exported when disableLogs is set to false`() {
        application.pluginOptions = application.pluginOptions.copy(disableLogs = false)
        application.initForTest()
        val logsUrl = "http://localhost:${application.mockWebServer?.port}/v1/logs"

        triggerTestLog()
        LDObserve.flush()
        waitForTelemetryData(telemetryInspector = application.telemetryInspector, telemetryType = TelemetryType.LOGS)

        assertNotNull(application.telemetryInspector?.logExporter)
        assertTrue(requestsContainsUrl(logsUrl))
    }

    @Test
    fun `Spans should NOT be exported when disableTraces is set to true`() {
        application.pluginOptions = application.pluginOptions.copy(disableTraces = true)
        application.initForTest()
        val tracesUrl = "http://localhost:${application.mockWebServer?.port}/v1/traces"

        triggerTestSpan()
        LDObserve.flush()
        waitForTelemetryData(telemetryInspector = application.telemetryInspector, telemetryType = TelemetryType.SPANS)

        assertNull(application.telemetryInspector?.spanExporter)
        assertFalse(requestsContainsUrl(tracesUrl))
    }

    @Test
    fun `Spans should be exported when disableTraces is set to false`() {
        application.pluginOptions = application.pluginOptions.copy(disableTraces = false)
        application.initForTest()
        val tracesUrl = "http://localhost:${application.mockWebServer?.port}/v1/traces"

        triggerTestSpan()
        LDObserve.flush()
        waitForTelemetryData(telemetryInspector = application.telemetryInspector, telemetryType = TelemetryType.SPANS)

        assertNotNull(application.telemetryInspector?.spanExporter)
        assertTrue(requestsContainsUrl(tracesUrl))
    }

    @Test
    fun `Metrics should NOT be exported when disableMetrics is set to true`() {
        application.pluginOptions = application.pluginOptions.copy(disableMetrics = true)
        application.initForTest()
        val metricsUrl = "http://localhost:${application.mockWebServer?.port}/v1/metrics"

        triggerTestMetric()
        LDObserve.flush()
        waitForTelemetryData(telemetryInspector = application.telemetryInspector, telemetryType = TelemetryType.METRICS)

        assertNull(application.telemetryInspector?.metricExporter)
        assertFalse(requestsContainsUrl(metricsUrl))
    }

    @Test
    fun `Metrics should be exported when disableMetrics is set to false`() {
        application.pluginOptions = application.pluginOptions.copy(disableMetrics = false)
        application.initForTest()
        val metricsUrl = "http://localhost:${application.mockWebServer?.port}/v1/metrics"

        triggerTestMetric()
        LDObserve.flush()
        waitForTelemetryData(telemetryInspector = application.telemetryInspector, telemetryType = TelemetryType.METRICS)

        assertNotNull(application.telemetryInspector?.metricExporter)
        assertTrue(requestsContainsUrl(metricsUrl))
    }

    @Test
    fun `Errors should NOT be exported when disableErrorTracking is set to true`() {
        application.pluginOptions = application.pluginOptions.copy(disableErrorTracking = true)
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
    fun `Errors should be exported when disableErrorTracking is set to false`() {
        application.pluginOptions = application.pluginOptions.copy(disableErrorTracking = false)
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

    private fun requestsContainsUrl(url: String): Boolean {
        while (true) {
            val request = application.mockWebServer?.takeRequest(100, TimeUnit.MILLISECONDS)
            if (request == null) return false
            if (request.requestUrl.toString() == url) return true
        }
    }

    private fun triggerTestLog() {
        LDObserve.recordLog(
            message = "test-log",
            severity = Severity.INFO,
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
}
