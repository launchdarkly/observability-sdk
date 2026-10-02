package com.launchdarkly.observability.testing

import com.launchdarkly.observability.client.TelemetryInspector
import io.opentelemetry.sdk.testing.exporter.InMemoryLogRecordExporter
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricExporter
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter

/**
 * [TelemetryInspector] backed by in-memory exporters from the OpenTelemetry SDK testing library.
 *
 * Provides concrete [InMemorySpanExporter], [InMemoryLogRecordExporter], and
 * [InMemoryMetricExporter] instances so tests can assert on exported telemetry data.
 */
class InMemoryTelemetryInspector : TelemetryInspector {
    override val spanExporter: InMemorySpanExporter by lazy { InMemorySpanExporter.create() }
    override val logExporter: InMemoryLogRecordExporter by lazy { InMemoryLogRecordExporter.create() }
    override val metricExporter: InMemoryMetricExporter by lazy { InMemoryMetricExporter.create() }
}
