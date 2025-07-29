package com.launchdarkly.observability.client

import com.launchdarkly.observability.interfaces.Metric
import com.launchdarkly.sdk.LDValue
import com.launchdarkly.sdk.android.LDClient
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import io.opentelemetry.context.Context
import io.opentelemetry.exporter.otlp.http.logs.OtlpHttpLogRecordExporter
import io.opentelemetry.exporter.otlp.http.metrics.OtlpHttpMetricExporter
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.logs.SdkLoggerProvider
import io.opentelemetry.sdk.logs.export.BatchLogRecordProcessor
import io.opentelemetry.sdk.metrics.SdkMeterProvider
import io.opentelemetry.sdk.metrics.export.MetricExporter
import io.opentelemetry.sdk.metrics.export.PeriodicMetricReader
import io.opentelemetry.sdk.resources.Resource
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor
import java.util.concurrent.TimeUnit

private const val URL = "https://otel.observability.app.launchdarkly.com:4318"
private const val URL_METRICS = URL + "/v1/metrics"
private const val URL_LOGS = URL + "/v1/logs"
private const val URL_TRACES = URL + "/v1/traces"

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
    private var meterProvider: SdkMeterProvider
    private var loggerProvider: SdkLoggerProvider
    private var tracerProvider: SdkTracerProvider

    init {
        meterProvider = createMetricsProvider()
        loggerProvider = createLoggerProvider()
        tracerProvider = createTracerProvider()

        // TODO: pretty sure registering globally is a bad idea, but I'm not sure what the norm is here.
        // I see documentation that talks about being able to signal to the auto configuration system
        // via a specially named configurer implementation: autoconfigure SPI interface: SdkTracerProviderConfigurer
        OpenTelemetrySdk.builder()
            .setMeterProvider(meterProvider)
            .setLoggerProvider(loggerProvider)
            .setTracerProvider(tracerProvider)
            .buildAndRegisterGlobal()

    }

    fun recordMetric(metric: Metric) {
        // TODO: should we hold a reference to the meter?
        meterProvider.get("com.launchdarkly.observability").gaugeBuilder(metric.name).build()
            .set(metric.value, metric.attributes)

        // TODO: O11Y-362: convert attributes to LDValue object and pass instead of LDValue.ofNull()
        client.trackMetric(metric.name, LDValue.ofNull(), metric.value)
    }

    fun recordCount(metric: Metric) {
        // TODO: should we hold a reference to the meter?
        // TODO: how to handle long and double casting?
        meterProvider.get("com.launchdarkly.observability").counterBuilder(metric.name).build()
            .add(metric.value.toLong(), metric.attributes)
    }

    fun recordIncr(metric: Metric) {
        // TODO: should we hold a reference to the meter?
        meterProvider.get("com.launchdarkly.observability").counterBuilder(metric.name).build()
            .add(1, metric.attributes)
    }

    fun recordHistogram(metric: Metric) {
        // TODO: should we hold a reference to the meter?
        meterProvider.get("com.launchdarkly.observability").histogramBuilder(metric.name).build()
            .record(metric.value, metric.attributes)
    }

    fun recordUpDownCounter(metric: Metric) {
        // TODO: should we hold a reference to the meter?
        meterProvider.get("com.launchdarkly.observability").upDownCounterBuilder(metric.name)
            .build().add(metric.value.toLong(), metric.attributes)
    }

    fun recordLog(message: String, level: String, attributes: Attributes) {
        loggerProvider.get("com.launchdarkly.observability").logRecordBuilder()
            .setBody(message)
            .setTimestamp(System.currentTimeMillis(), TimeUnit.MILLISECONDS)
            .setAttribute("severityText", level)
            // TODO: add highlight.session_id when sessions are supported
            .setAllAttributes(attributes)
            .emit()
    }

    // TODO: add otel span optional param that will take precedence over current span and/or created span
    fun recordError(error: Error, attributes: Attributes) {
        // TODO: add error and attributes to span
        // TODO: add highlight.session_id when sessions are supported
        val span = tracerProvider.get("com.launchdarkly.observability")
            .spanBuilder("highlight.error")
            .setParent(
                Context.current().with(Span.current())
            )
            .startSpan()

        val attrBuilder = Attributes.builder()

        // TODO: should exception.cause be added here?  At least one other SDK is doing this
//        error.cause?.let {
//            span.setAttribute("exception.cause", it.message)
//        }

        span.recordException(error, attrBuilder.build())
        span.end()
    }

    fun startSpan(name: String, attributes: Attributes): Span {
        return tracerProvider.get("com.launchdarkly.observability").spanBuilder(name)
            .setParent(Context.current().with(Span.current()))
            .setAllAttributes(attributes)
            .startSpan()
    }

    private fun createMetricsProvider(): SdkMeterProvider {
        // Build a default OTLP HTTP exporter. Users can swap this out later if
        // they wish to use a different exporter implementation.
        val metricExporter: MetricExporter = OtlpHttpMetricExporter.builder()
            .setEndpoint(URL_METRICS)
            .addHeader("X-LaunchDarkly-Project", sdkKey)
            .build()

        // Configure a periodic reader that pushes metrics every 10 seconds.
        val metricReader: PeriodicMetricReader =
            PeriodicMetricReader.builder(metricExporter)
                .setInterval(10, TimeUnit.SECONDS)
                .build()

        // Build the SDK MeterProvider with the supplied resources and the
        // configured metric reader.
        return SdkMeterProvider.builder()
            .setResource(resources)
            .registerMetricReader(metricReader)
            .build()
    }

    private fun createLoggerProvider(): SdkLoggerProvider {
        val logExporter = OtlpHttpLogRecordExporter.builder()
            .setEndpoint(URL_LOGS)
            .addHeader("X-LaunchDarkly-Project", sdkKey)
            .build()

        // TODO: are these configuration values supposed to be passed in?
        val processor = BatchLogRecordProcessor.builder(logExporter)
            .setMaxQueueSize(100)
            .setScheduleDelay(500, TimeUnit.MILLISECONDS)
            .setExporterTimeout(5000, TimeUnit.MILLISECONDS)
            .setMaxExportBatchSize(10)
            .build()

        return SdkLoggerProvider.builder()
            .setResource(resources)
            .addLogRecordProcessor(processor)
            .build()
    }

    private fun createTracerProvider(): SdkTracerProvider {

        // TODO: should we set a global propagator?

        val spanExporter = OtlpHttpSpanExporter.builder()
            .setEndpoint(URL_TRACES)
            .addHeader("X-LaunchDarkly-Project", sdkKey)
            .build()

        val spanProcessor = BatchSpanProcessor.builder(spanExporter)
            .setMaxQueueSize(100)
            .setScheduleDelay(500, TimeUnit.MILLISECONDS)
            .setExporterTimeout(5000, TimeUnit.MILLISECONDS)
            .setMaxExportBatchSize(10)
            .build()

        // TODO: O11Y-370: register activity lifecycle and fragment lifecycle trace providers

        return SdkTracerProvider.builder()
            .setResource(resources)
            .addSpanProcessor(spanProcessor)
            .build()
    }
}