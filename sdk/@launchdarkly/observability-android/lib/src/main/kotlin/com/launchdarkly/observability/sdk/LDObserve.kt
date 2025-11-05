package com.launchdarkly.observability.sdk

import com.launchdarkly.observability.client.ObservabilityClient
import com.launchdarkly.observability.interfaces.Metric
import com.launchdarkly.observability.interfaces.Observe
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.logs.Severity
import io.opentelemetry.api.trace.Span

/**
 * LDObserve is the singleton entry point for recording observability data such as
 * metrics, logs, errors, and traces. It is recommended to use the [com.launchdarkly.observability.plugin.Observability] plugin
 * with the LaunchDarkly Android Client SDK, as that will automatically initialize the [LDObserve] singleton instance.
 *
 * @constructor Creates an LDObserve instance with the provided [Observe].
 * @param client The [Observe] to which observability data will be forwarded.
 */
class LDObserve(private val client: Observe) : Observe {

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

    override fun flush(): Boolean {
        return client.flush()
    }

    companion object : Observe {
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
                return Span.getInvalid() // Observability plugin was not initialized before being used.
            }
            override fun flush(): Boolean {
                return false // No-op, return false to indicate flush was not successful
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
        override fun flush(): Boolean = delegate.flush()
    }
}
