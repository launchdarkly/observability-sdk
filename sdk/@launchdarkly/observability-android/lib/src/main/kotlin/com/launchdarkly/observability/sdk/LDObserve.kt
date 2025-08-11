package com.launchdarkly.observability.sdk

import com.launchdarkly.observability.client.ObservabilityClient
import com.launchdarkly.observability.interfaces.Metric
import com.launchdarkly.observability.interfaces.Observe
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.logs.Severity
import io.opentelemetry.api.trace.Span

class LDObserve(private val client: ObservabilityClient) : Observe {
    override fun recordMetric(metric: Metric) {
        client.recordMetric(metric)
    }

    override fun recordCount(metric: Metric) {
        client.recordCount(metric)
    }

    override fun recordIncr(metric: Metric) {
        client.recordIncr(metric)
    }

    override fun recordHistogram(metric: Metric) {
        client.recordHistogram(metric)
    }

    override fun recordUpDownCounter(metric: Metric) {
        client.recordUpDownCounter(metric)
    }

    override fun recordError(error: Error, attributes: Attributes) {
        client.recordError(error, attributes)
    }

    override fun recordLog(message: String, severity: Severity, attributes: Attributes) {
        client.recordLog(message, severity, attributes)
    }

    override fun startSpan(name: String, attributes: Attributes): Span {
        return client.startSpan(name, attributes)
    }

    companion object : Observe{
        // initially a no-op delegate
        // volatile annotation guarantees multiple threads see the same value after init and none continue using the no-op implementation
        @Volatile
        private var delegate: Observe = object : Observe {
            override fun recordMetric(metric: Metric) {}
            override fun recordCount(metric: Metric) {}
            override fun recordIncr(metric: Metric) {}
            override fun recordHistogram(metric: Metric) {}
            override fun recordUpDownCounter(metric: Metric) {}
            override fun recordError(error: Error, attributes: Attributes) {}
            override fun recordLog(message: String, severity: Severity, attributes: Attributes) {}
            override fun startSpan(name: String, attributes: Attributes): Span {
                // TODO: figure out if a no-op span implementation exists in the otel library
                throw IllegalStateException("Observability plugin was not initialized before being used.")
            }
        }

        fun init(client: ObservabilityClient) {
            delegate = LDObserve(client)
        }

        override fun recordMetric(metric: Metric) = delegate.recordMetric(metric)
        override fun recordCount(metric: Metric) = delegate.recordCount(metric)
        override fun recordIncr(metric: Metric) = delegate.recordIncr(metric)
        override fun recordHistogram(metric: Metric) = delegate.recordHistogram(metric)
        override fun recordUpDownCounter(metric: Metric) = delegate.recordUpDownCounter(metric)
        override fun recordError(error: Error, attributes: Attributes) = delegate.recordError(error, attributes)
        override fun recordLog(message: String, severity: Severity, attributes: Attributes) = delegate.recordLog(message, severity, attributes)
        override fun startSpan(name: String, attributes: Attributes): Span = delegate.startSpan(name, attributes)
    }
}
