package com.launchdarkly.observability.client

import android.app.Application
import com.launchdarkly.logging.LDLogger
import com.launchdarkly.observability.api.Options
import com.launchdarkly.observability.interfaces.LDExtendedInstrumentation
import com.launchdarkly.observability.interfaces.Metric
import com.launchdarkly.observability.interfaces.Observe
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.logs.Severity
import io.opentelemetry.api.trace.Span
import io.opentelemetry.sdk.resources.Resource

/**
 * The [ObservabilityClient] can be used for recording observability data such as
 * metrics, logs, errors, and traces.
 *
 * It is recommended to use the [com.launchdarkly.observability.plugin.Observability] plugin with the LaunchDarkly Android
 * Client SDK, as that will automatically initialize the [com.launchdarkly.observability.sdk.LDObserve] singleton instance.
 *
 */
class ObservabilityClient : Observe {
    private val instrumentationManager: InstrumentationManager

    /**
     * Creates a new ObservabilityClient.
     *
     * @param application The application instance.
     * @param sdkKey The SDK key for the environment.
     * @param resource The resource.
     * @param logger The logger.
     * @param options Additional options for the client.
     * @param instrumentations A list of extended instrumentation providers.
     */
    constructor(
        application: Application,
        sdkKey: String,
        resource: Resource,
        logger: LDLogger,
        options: Options,
        instrumentations: List<LDExtendedInstrumentation>
    ) {
        this.instrumentationManager = InstrumentationManager(
            application, sdkKey, resource, logger, options, instrumentations
        )
    }

    internal constructor(
        instrumentationManager: InstrumentationManager
    ) {
        this.instrumentationManager = instrumentationManager
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

    override fun recordLog(message: String, severity: Severity, attributes: Attributes) {
        instrumentationManager.recordLog(message, severity, attributes)
    }

    override fun startSpan(name: String, attributes: Attributes): Span {
        return instrumentationManager.startSpan(name, attributes)
    }

    /**
     * Returns the telemetry inspector for accessing intercepted telemetry data.
     *
     * This method provides access to spans and logs that have been exported by the SDK
     * for debugging, testing, or other purposes. The inspector is only available
     * if debug was enabled via "Options.debug".
     *
     * @return TelemetryInspector instance if debug is enabled, null otherwise
     */
    fun getTelemetryInspector(): TelemetryInspector? {
        return instrumentationManager.getTelemetryInspector()
    }

    /**
     * Returns the tracer instance for creating spans.
     *
     * @return Tracer instance
     */
    fun getTracer() = instrumentationManager.getTracer()

    override fun flush(): Boolean {
        return instrumentationManager.flush()
    }
}
