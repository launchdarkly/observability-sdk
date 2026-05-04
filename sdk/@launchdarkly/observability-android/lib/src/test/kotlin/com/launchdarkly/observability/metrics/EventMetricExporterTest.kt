package com.launchdarkly.observability.metrics

import com.launchdarkly.observability.replay.transport.EventQueue
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.sdk.metrics.InstrumentType
import io.opentelemetry.sdk.metrics.data.AggregationTemporality
import io.opentelemetry.sdk.metrics.data.LongPointData
import io.opentelemetry.sdk.metrics.export.AggregationTemporalitySelector
import io.opentelemetry.sdk.metrics.internal.data.ImmutableLongPointData
import io.opentelemetry.sdk.metrics.internal.data.ImmutableSumData
import io.opentelemetry.sdk.testing.metrics.TestMetricData
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class EventMetricExporterTest {

    @Test
    fun `export wraps each MetricData in a MetricItemPayload and enqueues them`() {
        val queue = EventQueue()
        val exporter = EventMetricExporter(queue, clock = { 12_345L })

        val metrics = listOf(buildLongSumMetric("a"), buildLongSumMetric("b"))
        val result = exporter.export(metrics)
        assertTrue(result.isSuccess)

        val batch = queue.earliest(
            costBudget = Int.MAX_VALUE,
            limit = 10,
            except = emptySet(),
        )
        assertEquals(OtlpMetricExporter::class.java, batch!!.exporterClass)
        assertEquals(2, batch.items.size)

        val first = batch.items[0].payload as MetricItemPayload
        assertEquals(12_345L, first.timestamp)
        assertSame(metrics[0], first.metricData)
    }

    @Test
    fun `export returns success without enqueueing when there are no metrics`() {
        val queue = EventQueue()
        val exporter = EventMetricExporter(queue)

        assertTrue(exporter.export(emptyList()).isSuccess)
        assertEquals(
            null,
            queue.earliest(costBudget = Int.MAX_VALUE, limit = 10, except = emptySet()),
        )
    }

    @Test
    fun `getAggregationTemporality defaults to delta preferred`() {
        val exporter = EventMetricExporter(EventQueue())

        assertEquals(
            AggregationTemporality.CUMULATIVE,
            exporter.getAggregationTemporality(InstrumentType.UP_DOWN_COUNTER),
        )
        assertEquals(
            AggregationTemporality.DELTA,
            exporter.getAggregationTemporality(InstrumentType.COUNTER),
        )
    }

    @Test
    fun `getAggregationTemporality delegates to supplied selector`() {
        val exporter = EventMetricExporter(
            EventQueue(),
            temporalitySelector = AggregationTemporalitySelector.alwaysCumulative(),
        )
        assertEquals(
            AggregationTemporality.CUMULATIVE,
            exporter.getAggregationTemporality(InstrumentType.COUNTER),
        )
    }

    private fun buildLongSumMetric(name: String): io.opentelemetry.sdk.metrics.data.MetricData {
        val point = ImmutableLongPointData.create(
            1L,
            2L,
            Attributes.empty(),
            1L,
        )
        return TestMetricData.builder()
            .setName(name)
            .setDescription("")
            .setUnit("")
            .setLongSumData(
                ImmutableSumData.create(
                    /* isMonotonic = */ true,
                    AggregationTemporality.DELTA,
                    listOf<LongPointData>(point),
                )
            )
            .build()
    }
}
