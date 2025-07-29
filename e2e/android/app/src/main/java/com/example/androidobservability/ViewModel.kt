package com.example.androidobservability

import LDObserve
import androidx.lifecycle.ViewModel
import com.launchdarkly.observability.interfaces.Metric
import com.launchdarkly.sdk.android.LDClient
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
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
            "debug",
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
        lastSpan?.end()
        lastSpan = null
    }
}