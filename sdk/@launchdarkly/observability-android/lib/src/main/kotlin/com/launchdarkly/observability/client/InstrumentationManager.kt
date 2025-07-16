package com.launchdarkly.observability.client
import io.opentelemetry.api.metrics.MeterProvider
import io.opentelemetry.exporter.otlp.http.metrics.OtlpHttpMetricExporter
import io.opentelemetry.sdk.metrics.SdkMeterProvider
import io.opentelemetry.sdk.metrics.export.MetricExporter
import io.opentelemetry.sdk.metrics.export.PeriodicMetricReader
import io.opentelemetry.sdk.resources.Resource
import java.util.concurrent.TimeUnit

/**
 * Manages instrumentation for LaunchDarkly Observability.
 *
 * @param resources The OpenTelemetry resource describing this service.
 */
class InstrumentationManager(resources: Resource) {

    private val meterProvider: MeterProvider

    init {
        // Build a default OTLP gRPC exporter. Users can swap this out later if
        // they wish to use a different exporter implementation.
        val metricExporter: MetricExporter = OtlpHttpMetricExporter.builder().build()

        // Configure a periodic reader that pushes metrics every 10 seconds.
        val metricReader: PeriodicMetricReader =
            PeriodicMetricReader.builder(metricExporter)
                .setInterval(10, TimeUnit.SECONDS)
                .build()

        // Build the SDK MeterProvider with the supplied resources and the
        // configured metric reader.
        meterProvider = SdkMeterProvider.builder()
            .setResource(resources)
            .registerMetricReader(metricReader)
            .build()

        // Example usage – create a meter for this library.
        // val meter = meterProvider.get("com.launchdarkly.observability")
    }
}