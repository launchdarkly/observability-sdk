package com.launchdarkly.observability.client

import android.app.Application
import com.launchdarkly.observability.client.InstrumentationManager
import com.launchdarkly.observability.interfaces.Metric
import com.launchdarkly.observability.interfaces.Observe
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import io.opentelemetry.sdk.resources.Resource

public class ObservabilityClient: Observe {
    private val instrumentationManager: InstrumentationManager

    constructor(
        application: Application,
        sdkKey: String,
        resource: Resource
    ) {
        this.instrumentationManager = InstrumentationManager(application, sdkKey, resource)
    }

    override fun recordMetric(metric: Metric) {
        instrumentationManager.recordMetric(metric)
    }

    override fun recordCount(metric: Metric) {
        instrumentationManager.recordCount(metric)
    }

    override fun recordIncr(metric: Metric) {
        instrumentationManager.recordIncr(metric)
    }

    override fun recordHistogram(metric: Metric) {
        instrumentationManager.recordHistogram(metric)
    }

    override fun recordUpDownCounter(metric: Metric) {
        instrumentationManager.recordUpDownCounter(metric)
    }

    override fun recordError(error: Error, attributes: Attributes) {
        instrumentationManager.recordError(error, attributes)
    }

    override fun recordLog(message: String, level: String, attributes: Attributes) {
        instrumentationManager.recordLog(message, level, attributes)
    }

    override fun startSpan(name: String, attributes: Attributes): Span {
        return instrumentationManager.startSpan(name, attributes)
    }
}
