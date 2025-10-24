package com.example.androidobservability

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.example.androidobservability.TestUtils.TelemetryType
import com.example.androidobservability.TestUtils.waitForTelemetryData
import com.launchdarkly.observability.api.Options
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
    fun `Logs should NOT be exported when disableLogs is set to true`() {
        application.pluginOptions = getOptionsAllEnabled().copy(disableLogs = true)
        application.initForTest()
        val logsUrl = "http://localhost:${application.mockWebServer?.port}/v1/logs"

        triggerTestLog()
        LDObserve.flush()
        waitForTelemetryData(telemetryInspector = application.telemetryInspector, telemetryType = TelemetryType.LOGS)
        val logsExported = application.telemetryInspector?.logExporter?.finishedLogRecordItems

        assertTrue(logsExported?.isEmpty() == true)
        assertFalse(requestsContainsUrl(logsUrl))
    }

    @Test
    fun `Logs should be exported when disableLogs is set to false`() {
        application.pluginOptions = getOptionsAllEnabled().copy(disableLogs = false)
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
        application.pluginOptions = getOptionsAllEnabled().copy(disableTraces = true)
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
    fun `Spans should be exported when disableTraces is set to false`() {
        application.pluginOptions = getOptionsAllEnabled().copy(disableTraces = false)
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
        application.pluginOptions = getOptionsAllEnabled().copy(disableMetrics = true)
        application.initForTest()
        val metricsUrl = "http://localhost:${application.mockWebServer?.port}/v1/metrics"

        triggerTestMetric()
        LDObserve.flush()
        waitForTelemetryData(telemetryInspector = application.telemetryInspector, telemetryType = TelemetryType.METRICS)

        assertFalse(requestsContainsUrl(metricsUrl))
    }

    @Test
    fun `Metrics should be exported when disableMetrics is set to false`() {
        application.pluginOptions = getOptionsAllEnabled().copy(disableMetrics = false)
        application.initForTest()
        val metricsUrl = "http://localhost:${application.mockWebServer?.port}/v1/metrics"

        triggerTestMetric()
        LDObserve.flush()
        waitForTelemetryData(telemetryInspector = application.telemetryInspector, telemetryType = TelemetryType.METRICS)

        assertTrue(requestsContainsUrl(metricsUrl))
    }

    @Test
    fun `Errors should NOT be exported when disableErrorTracking is set to true`() {
        application.pluginOptions = getOptionsAllEnabled().copy(disableErrorTracking = true)
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
    fun `Errors should be exported as spans when disableErrorTracking is set to false and disableTraces set to true`() {
        application.pluginOptions = getOptionsAllEnabled().copy(disableTraces = true, disableErrorTracking = false)
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
    fun `Crashes should NOT be exported when disableErrorTracking is set to true`() {
        application.pluginOptions = getOptionsAllEnabled().copy(disableErrorTracking = true)
        application.initForTest()
        val logsUrl = "http://localhost:${application.mockWebServer?.port}/v1/logs"

        Thread { throw RuntimeException("Exception for testing") }.start()

        waitForTelemetryData(telemetryInspector = application.telemetryInspector, telemetryType = TelemetryType.LOGS)
        val logsExported = application.telemetryInspector?.logExporter?.finishedLogRecordItems

        assertFalse(requestsContainsUrl(logsUrl))
        assertEquals(0, logsExported?.size)
    }

    @Test
    fun `Crashes should be exported as logs when disableErrorTracking is set to false and disableLogs set to true`() {
        application.pluginOptions = getOptionsAllEnabled().copy(disableLogs = true, disableErrorTracking = false)
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

    private fun getOptionsAllEnabled(): Options {
        return Options(
            debug = true,
            disableTraces = false,
            disableLogs = false,
            disableMetrics = false,
            disableErrorTracking = false
        )
    }
}
