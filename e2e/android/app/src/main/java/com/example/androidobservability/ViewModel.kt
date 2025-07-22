package com.example.androidobservability

import LDObserve
import androidx.lifecycle.ViewModel
import com.launchdarkly.observability.interfaces.Metric

class ViewModel : ViewModel() {
    fun triggerMetric() {
        LDObserve.recordMetric(Metric("test", 50.0))
    }
}