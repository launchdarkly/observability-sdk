package com.example.androidobservability

import com.launchdarkly.observability.client.TelemetryInspector

object TestUtils {

    fun waitForTelemetryData(
        maxWaitMs: Long = 5000,
        telemetryInspector: TelemetryInspector?,
        telemetryType: TelemetryType
    ): Boolean {
        val startTime = System.currentTimeMillis()
        while (System.currentTimeMillis() - startTime < maxWaitMs) {
            val hasData = when (telemetryType) {
                TelemetryType.SPANS -> telemetryInspector?.spanExporter?.finishedSpanItems?.isNotEmpty() == true
                TelemetryType.LOGS -> telemetryInspector?.logExporter?.finishedLogRecordItems?.isNotEmpty() == true
                TelemetryType.METRICS -> telemetryInspector?.metricExporter?.finishedMetricItems?.isNotEmpty() == true
            }

            if (hasData) return true
            Thread.sleep(100)
        }
        return false
    }

    enum class TelemetryType {
        SPANS, LOGS, METRICS
    }
}
