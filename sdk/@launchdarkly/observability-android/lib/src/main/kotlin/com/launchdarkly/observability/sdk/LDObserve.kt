package com.launchdarkly.observability.sdk

import com.launchdarkly.observability.bridge.AttributeConverter
import com.launchdarkly.observability.client.ObservabilityService
import com.launchdarkly.observability.client.ObservabilityContext
import com.launchdarkly.observability.interfaces.Metric
import com.launchdarkly.observability.interfaces.Observe
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.logs.Severity
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanContext

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

    override fun recordLog(message: String, severity: Severity, attributes: Attributes, spanContext: SpanContext?) {
        client.recordLog(message, severity, attributes, spanContext)
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
            override fun recordLog(message: String, severity: Severity, attributes: Attributes, spanContext: SpanContext?) {}
            override fun startSpan(name: String, attributes: Attributes): Span {
                return Span.getInvalid() // Observability plugin was not initialized before being used.
            }
            override fun flush(): Boolean {
                return false // No-op, return false to indicate flush was not successful
            }
        }

        /**
         * Shared context for other plugins (e.g. Session Replay) to access Observability configuration and dependencies.
         */
        @Volatile
        var context: ObservabilityContext? = null
            internal set

        @Volatile
        internal var observabilityClient: ObservabilityService? = null
            private set

        fun init(client: ObservabilityService) {
            observabilityClient = client
            delegate = LDObserve(client)
        }

        override fun recordMetric(metric: Metric) = delegate.recordMetric(metric)
        override fun recordCount(metric: Metric) = delegate.recordCount(metric)
        override fun recordIncr(metric: Metric) = delegate.recordIncr(metric)
        override fun recordHistogram(metric: Metric) = delegate.recordHistogram(metric)
        override fun recordUpDownCounter(metric: Metric) = delegate.recordUpDownCounter(metric)
        override fun recordError(error: Error, attributes: Attributes) = delegate.recordError(error, attributes)
        override fun recordLog(message: String, severity: Severity, attributes: Attributes, spanContext: SpanContext?) = delegate.recordLog(message, severity, attributes, spanContext)
        override fun startSpan(name: String, attributes: Attributes): Span = delegate.startSpan(name, attributes)
        override fun flush(): Boolean = delegate.flush()

        /**
         * Bridge-friendly overloads that avoid exposing OpenTelemetry types
         * to callers such as the .NET MAUI native bridge.
         */

        fun recordError(message: String, cause: String? = null) {
            val error = Error(message, if (cause != null) Throwable(cause) else null)
            delegate.recordError(error, Attributes.empty())
        }

        fun recordLog(message: String, severityNumber: Int, attributes: Map<String, Any?>? = null) {
            val severity = Severity.values().firstOrNull { it.severityNumber == severityNumber }
                ?: Severity.INFO
            val attrs = AttributeConverter.convert(attributes)
            delegate.recordLog(message, severity, attrs)
        }
    }
}
