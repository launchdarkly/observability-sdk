package com.launchdarkly.observability.sdk

import com.launchdarkly.observability.client.ObservabilityContext
import com.launchdarkly.observability.client.ObservabilityService
import com.launchdarkly.observability.interfaces.Metric
import com.launchdarkly.observability.interfaces.Observe
import com.launchdarkly.observability.replay.plugin.SessionReplayImpl
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.logs.Severity
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanContext

/**
 * Internal singleton that owns the mutable state backing [LDObserve] and exposes
 * APIs that are intended for use by other components of the observability SDK
 * only (the Observability plugin, Session Replay, and the .NET MAUI bridge layer).
 *
 * Customers should not depend on this object; the customer-facing API lives on
 * [LDObserve].
 */
internal object LDObserveInternal {

    /**
     * Active [Observe] implementation that the [LDObserve] companion forwards to.
     *
     * Defaults to a no-op implementation so calls made before initialization are
     * safe. `@Volatile` guarantees that, once initialized, all threads observe the
     * same delegate and none keep using the no-op implementation.
     */
    @Volatile
    var delegate: Observe = NoOpObserve
        private set

    /**
     * Shared context for other plugins (e.g. Session Replay) to access
     * Observability configuration and dependencies.
     */
    @Volatile
    var context: ObservabilityContext? = null

    /**
     * The active [ObservabilityService], or `null` if observability has not been
     * initialized yet.
     */
    @Volatile
    var observabilityClient: ObservabilityService? = null
        private set

    /**
     * Session Replay plugin instance created by the standalone [LDObserve.init]
     * path, retained so it lives for the duration of the process.
     */
    @Volatile
    var sessionReplayPlugin: SessionReplayImpl? = null

    /**
     * Wires [LDObserve] to forward all telemetry to the supplied
     * [ObservabilityService]. Called by the
     * [com.launchdarkly.observability.plugin.Observability] plugin and by the
     * standalone [LDObserve.init] path.
     */
    fun init(client: ObservabilityService) {
        observabilityClient = client
        delegate = LDObserve(client)
    }
}

private object NoOpObserve : Observe {
    override fun recordMetric(metric: Metric) {}
    override fun recordCount(metric: Metric) {}
    override fun recordIncr(metric: Metric) {}
    override fun recordHistogram(metric: Metric) {}
    override fun recordUpDownCounter(metric: Metric) {}
    override fun recordError(error: Error, attributes: Attributes) {}
    override fun recordLog(
        message: String,
        severity: Severity,
        attributes: Attributes,
        spanContext: SpanContext?
    ) {
    }

    override fun startSpan(name: String, attributes: Attributes): Span = Span.getInvalid()
    override fun flush(): Boolean = false
}
