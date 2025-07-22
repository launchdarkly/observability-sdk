package com.launchdarkly.observability.client
import com.launchdarkly.observability.interfaces.Metric
import com.launchdarkly.sdk.LDValue
import com.launchdarkly.sdk.android.LDClient
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
class InstrumentationManager(
    private val sdkKey: String,
    private val client: LDClient,
    private val resources: Resource,
) {
    private val meterProvider: MeterProvider

    init {
        // Build a default OTLP gRPC exporter. Users can swap this out later if
        // they wish to use a different exporter implementation.
        val metricExporter: MetricExporter = OtlpHttpMetricExporter.builder()
            .setEndpoint("https://otel.observability.app.launchdarkly.com:4318" + "/v1/metrics")
            .addHeader("X-LaunchDarkly-Project", sdkKey)
            .build()

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

        // TODO: does this meter provider need to be global?
    }

    fun recordMetric(metric: Metric) {
        // TODO: should we hold a reference to the meter?
        meterProvider.get("com.launchdarkly.observability").gaugeBuilder(metric.name).build().set(metric.value, metric.attributes)

        // TODO: convert attributes to LDValue object and pass instead of LDValue.ofNull()
        client.trackMetric(metric.name, LDValue.ofNull(), metric.value)
    }

    fun recordCount(metric: Metric) {
        // TODO: should we hold a reference to the meter?
        // TODO: how to handle long and double casting?
        meterProvider.get("com.launchdarkly.observability").counterBuilder(metric.name).build().add(metric.value.toLong(), metric.attributes)
    }

    fun recordIncr(metric: Metric) {
        // TODO: should we hold a reference to the meter?
        meterProvider.get("com.launchdarkly.observability").counterBuilder(metric.name).build().add(1, metric.attributes)
    }

    fun recordHistogram(metric: Metric) {
        // TODO: should we hold a reference to the meter?
        meterProvider.get("com.launchdarkly.observability").histogramBuilder(metric.name).build().record(metric.value, metric.attributes)
    }

    fun recordUpDownCounter(metric: Metric) {
        // TODO: should we hold a reference to the meter?
        meterProvider.get("com.launchdarkly.observability").upDownCounterBuilder(metric.name).build().add(metric.value.toLong(), metric.attributes)
    }
}