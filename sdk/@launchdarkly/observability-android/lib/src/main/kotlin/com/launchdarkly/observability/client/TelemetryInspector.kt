package com.launchdarkly.observability.client

import io.opentelemetry.sdk.testing.exporter.InMemoryLogRecordExporter
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter

/**
 * This class wraps the OpenTelemetry testing exporters
 *
 * @param spanExporter The in-memory span exporter to read from
 * @param logExporter The in-memory log exporter to read from
 */
class TelemetryInspector(
    val spanExporter: InMemorySpanExporter?,
    val logExporter: InMemoryLogRecordExporter?
)
