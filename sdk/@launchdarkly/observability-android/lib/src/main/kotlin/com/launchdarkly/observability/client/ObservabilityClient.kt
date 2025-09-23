package com.launchdarkly.observability.client

import android.app.Application
import com.launchdarkly.logging.LDLogger
import com.launchdarkly.observability.api.Options
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
 * It is recommended to use the [Observability] plugin with the LaunchDarkly Android
 * Client SDK, as that will automatically initialize the [LDObserve] singleton instance.
 *
 * @param application The application instance.
 * @param sdkKey The SDK key.
 * @param resource The resource.
 * @param logger The logger.
 * @param options Additional options for the client.
 */
class ObservabilityClient : Observe {
    private val instrumentationManager: InstrumentationManager

    constructor(
        application: Application,
        sdkKey: String,
        resource: Resource,
        logger: LDLogger,
        options: Options
    ) {
        this.instrumentationManager = InstrumentationManager(application, sdkKey, resource, logger, options)
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

    override fun flush(): Boolean {
        return instrumentationManager.flush()
    }
}
