package com.example.androidobservability

import android.util.Log
import com.launchdarkly.observability.sdk.LDObserve
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.logs.Severity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val TOTAL_DURATION_MS = 30_000L
private const val TICK_INTERVAL_MS = 5_000L
private const val TOTAL_TICKS = (TOTAL_DURATION_MS / TICK_INTERVAL_MS).toInt()

/**
 * Launches a 30-second logging routine.
 */
fun CoroutineScope.launchObservabilityLoggingTask(
    serviceType: String,
    onComplete: () -> Unit
): Job {
    val serviceAttribute = AttributeKey.stringKey("service_type")

    return launch {
        try {
            Log.d("observability-services", "Starting $serviceType service logging task")
            LDObserve.recordLog(
                message = "Starting $serviceType service logging task",
                severity = Severity.INFO,
                attributes = Attributes.of(serviceAttribute, serviceType)
            )

            repeat(TOTAL_TICKS) { index ->
                delay(TICK_INTERVAL_MS)
                val attributes = Attributes.builder()
                    .put(serviceAttribute, serviceType)
                    .put(AttributeKey.longKey("tick_index"), (index + 1).toLong())
                    .put(AttributeKey.longKey("elapsed_ms"), ((index + 1) * TICK_INTERVAL_MS))
                    .build()

                Log.d("observability-services", "[$serviceType] Heartbeat ${index + 1} of $TOTAL_TICKS")
                LDObserve.recordLog(
                    message = "[$serviceType] Heartbeat ${index + 1} of $TOTAL_TICKS",
                    severity = Severity.INFO,
                    attributes = attributes
                )
            }

            Log.d("observability-services", "$serviceType service logging task completed")
            LDObserve.recordLog(
                message = "$serviceType service logging task completed",
                severity = Severity.INFO,
                attributes = Attributes.of(serviceAttribute, serviceType)
            )

            onComplete()
        } catch (_: CancellationException) {
            Log.d("observability-services", "$serviceType service logging task cancelled")
            LDObserve.recordLog(
                message = "$serviceType service logging task cancelled",
                severity = Severity.INFO,
                attributes = Attributes.of(serviceAttribute, serviceType)
            )
        }
    }
}
