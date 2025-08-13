package com.example.androidobservability

import androidx.lifecycle.ViewModel
import com.launchdarkly.observability.interfaces.Metric
import com.launchdarkly.observability.sdk.LDObserve
import com.launchdarkly.sdk.android.LDClient
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.logs.Severity
import io.opentelemetry.api.trace.Span

class ViewModel : ViewModel() {

    private var lastSpan: Span? = null

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

    fun triggerStartSpan() {
        val newSpan = LDObserve.startSpan("FakeSpan", Attributes.empty())
        newSpan.makeCurrent()
        lastSpan = newSpan
        LDClient.get().boolVariation("my-boolean-flag", false)
    }

    fun triggerStopSpan() {
        // TODO O11Y-397: for some reason stopped spans are stacking, the current span might be the problem
        lastSpan?.end()
        lastSpan = null
    }
}
