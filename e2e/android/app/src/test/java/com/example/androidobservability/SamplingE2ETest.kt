package com.example.androidobservability

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.example.androidobservability.TestUtils.TelemetryType
import com.example.androidobservability.TestUtils.waitForTelemetryData
import com.launchdarkly.observability.client.TelemetryInspector
import com.launchdarkly.observability.sdk.LDObserve
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.logs.Severity
import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * End-to-end tests for the LaunchDarkly Observability SDK's sampling functionality.
 *
 * This test suite validates that telemetry data (logs and spans) are correctly filtered
 * based on sampling configuration rules. The sampling configuration is dynamically injected
 * into the TestApplication through a mock web server that serves the configuration from
 * `get_sampling_config_response.json` in the test resources, simulating real-world SDK behavior.
 *
 * **Sampling Logic:**
 * - `samplingRatio: 0.0` = All matching telemetry is discarded (0% pass through)
 * - `samplingRatio: 1.0` = All matching telemetry passes through (100% pass through)
 * - Telemetry that doesn't match any sampling rules passes through by default
 *
 * **Test Coverage:**
 * - Log sampling by severity, message content, regex patterns, and attributes
 * - Span sampling by name, events, attributes, and complex rule combinations
 * - Verification that only expected telemetry reaches the exporters
 *
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestApplication::class)
class SamplingE2ETest {

    @get:Rule
    val testCoroutineRule = TestCoroutineRule()

    private val application = ApplicationProvider.getApplicationContext<Application>() as TestApplication
    private var telemetryInspector: TelemetryInspector? = null

    @Before
    fun setUp() {
        application.initForTest()
        telemetryInspector = application.telemetryInspector
    }

    @Test
    fun `should avoid exporting logs matching sampling configuration for logs`() = runTest {
        triggerLogs()
        telemetryInspector?.logExporter?.flush()
        waitForTelemetryData(telemetryInspector = application.telemetryInspector, telemetryType = TelemetryType.LOGS)

        val logsExported = telemetryInspector?.logExporter?.finishedLogRecordItems?.map {
            it.bodyValue?.value.toString()
        }

        // Only first and final logs should be exported
        assertEquals(2, logsExported?.size)
        assertEquals("first-log", logsExported?.get(0))
        assertEquals("final-log", logsExported?.get(1))
    }

    @Test
    fun `should avoid exporting spans matching sampling configuration for spans`() = runTest {
        triggerSpans()
        telemetryInspector?.spanExporter?.flush()
        waitForTelemetryData(telemetryInspector = application.telemetryInspector, telemetryType = TelemetryType.SPANS)

        val spansExported = telemetryInspector?.spanExporter?.finishedSpanItems?.map {
            it.name
        }

        // Only first and final spans should be exported
        assertEquals(2, spansExported?.size)
        assertEquals("first-span", spansExported?.get(0))
        assertEquals("final-span", spansExported?.get(1))
    }

    private fun triggerLogs() {
        // Log 0: Should NOT be sampled (doesn't match any config)
        LDObserve.recordLog(
            message = "first-log",
            severity = Severity.INFO,
            attributes = Attributes.empty()
        )

        // Log 1: Matches severity "ERROR"
        LDObserve.recordLog(
            message = "log1",
            severity = Severity.ERROR,
            attributes = Attributes.empty()
        )

        // Log 2: Matches message "Connection failed"
        LDObserve.recordLog(
            message = "Connection failed",
            severity = Severity.INFO,
            attributes = Attributes.empty()
        )

        // Log 3: Matches message regex "Error: .*"
        LDObserve.recordLog(
            message = "Error: HTTP connection failed",
            severity = Severity.INFO,
            attributes = Attributes.empty()
        )

        // Log 4: Matches complex config with specific attributes
        LDObserve.recordLog(
            message = "Database connection failed",
            severity = Severity.WARN,
            attributes = Attributes.of(
                AttributeKey.stringKey("service.name"), "db-primary",
                AttributeKey.booleanKey("retry.enabled"), true,
                AttributeKey.longKey("retry.count"), 3L,
                AttributeKey.doubleKey("retry.timeout"), 15.5
            )
        )

        // Log 5: Should NOT be sampled (doesn't match any config)
        LDObserve.recordLog(
            message = "final-log",
            severity = Severity.INFO,
            attributes = Attributes.empty()
        )
    }

    private fun triggerSpans() {
        // Span 0: Should NOT be sampled (doesn't match any config)
        val span0 = LDObserve.startSpan(
            name = "first-span",
            attributes = Attributes.empty()
        )
        span0.end()

        // Span 1: Matches name "test-span" with samplingRatio 0 (should be discarded)
        val span1 = LDObserve.startSpan(
            name = "test-span",
            attributes = Attributes.empty()
        )
        span1.end()

        // Span 2: Matches name regex "test-span-\\d+" with samplingRatio 0 (should be discarded)
        val span2 = LDObserve.startSpan(
            name = "test-span-1",
            attributes = Attributes.empty()
        )
        span2.end()

        // Span 3: Has event "test-event" with samplingRatio 0 (should be discarded)
        val span3 = LDObserve.startSpan(
            name = "span-with-event",
            attributes = Attributes.empty()
        )
        span3.addEvent("test-event")
        span3.end()

        // Span 4: Has event matching regex "test-event-\\d+" with samplingRatio 0 (should be discarded)
        val span4 = LDObserve.startSpan(
            name = "span-with-numbered-event",
            attributes = Attributes.empty()
        )
        span4.addEvent("test-event-42")
        span4.end()

        // Span 5: Has attribute http.method=POST with samplingRatio 0 (should be discarded)
        val span5 = LDObserve.startSpan(
            name = "http-request",
            attributes = Attributes.of(
                AttributeKey.stringKey("http.method"), "POST"
            )
        )
        span5.end()

        // Span 6: Has event with specific attributes with samplingRatio 0 (should be discarded)
        val span6 = LDObserve.startSpan(
            name = "error-span",
            attributes = Attributes.empty()
        )
        span6.addEvent(
            "error-occurred",
            Attributes.of(
                AttributeKey.stringKey("error.type"), "network",
                AttributeKey.stringKey("db.error"), "Database connection failed",
                AttributeKey.longKey("error.code"), 503L
            )
        )
        span6.end()

        // Span 7: Should NOT be sampled (doesn't match any config)
        val span7 = LDObserve.startSpan(
            name = "final-span",
            attributes = Attributes.empty()
        )
        span7.end()
    }
}
