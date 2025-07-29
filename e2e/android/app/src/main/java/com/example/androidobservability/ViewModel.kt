package com.example.androidobservability

import LDObserve
import androidx.lifecycle.ViewModel
import com.launchdarkly.observability.interfaces.Metric
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes

class ViewModel : ViewModel() {
    fun triggerMetric() {
        LDObserve.recordMetric(Metric("test", 50.0))
    }

    fun triggerError() {
        LDObserve.recordError(
            Error("Manual error womp womp"),
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
}
