package com.launchdarkly.observability.client

import io.opentelemetry.sdk.logs.export.LogRecordExporter
import io.opentelemetry.sdk.metrics.export.MetricExporter
import io.opentelemetry.sdk.trace.export.SpanExporter

/**
 * Provides access to telemetry exporters for inspecting exported data during testing.
 *
 * Production code uses the base exporter types to wire into composite exporters.
 * Test code should use [com.launchdarkly.observability.testing.InMemoryTelemetryInspector]
 * which exposes the concrete in-memory exporter types for assertions.
 */
interface TelemetryInspector {
    val spanExporter: SpanExporter
    val logExporter: LogRecordExporter
    val metricExporter: MetricExporter
}
