package com.launchdarkly.observability.replay.transport

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class EventQueueTest {

    @Test
    fun `send respects total limit and reports full`() {
        val queue = EventQueue(totalCostLimit = 3, exporterCostLimit = 10)

        queue.send(TestPayload(timestamp = 1L, exporterClass = ExporterA::class.java, payloadCost = 2))
        assertFalse(queue.isFull())

        queue.send(TestPayload(timestamp = 2L, exporterClass = ExporterA::class.java, payloadCost = 1))
        assertTrue(queue.isFull())

        queue.send(TestPayload(timestamp = 3L, exporterClass = ExporterA::class.java, payloadCost = 1))

        val result = queue.earliest(costBudget = 10, limit = 10, except = emptySet())

        assertEquals(2, result?.items?.size)
        assertEquals(listOf(1L, 2L), result?.items?.map { it.timestamp })
    }

    @Test
    fun `send respects exporter limit`() {
        val queue = EventQueue(totalCostLimit = 10, exporterCostLimit = 3)

        queue.send(TestPayload(timestamp = 1L, exporterClass = ExporterA::class.java, payloadCost = 2))
        queue.send(TestPayload(timestamp = 2L, exporterClass = ExporterA::class.java, payloadCost = 2))

        val result = queue.earliest(costBudget = 10, limit = 10, except = emptySet())

        assertEquals(1, result?.items?.size)
        assertEquals(1L, result?.items?.first()?.timestamp)
    }

    @Test
    fun `earliest returns oldest exporter not in except`() {
        val queue = EventQueue(totalCostLimit = 10, exporterCostLimit = 10)

        queue.send(TestPayload(timestamp = 100L, exporterClass = ExporterA::class.java, payloadCost = 1))
        queue.send(TestPayload(timestamp = 50L, exporterClass = ExporterB::class.java, payloadCost = 1))

        val result = queue.earliest(costBudget = 10, limit = 10, except = emptySet())
        assertEquals(ExporterB::class.java, result?.exporterClass)

        val resultExcept = queue.earliest(costBudget = 10, limit = 10, except = setOf(ExporterB::class.java),)
        assertEquals(ExporterA::class.java, resultExcept?.exporterClass)
    }

    @Test
    fun `removeFirst drops items and clears empty exporter`() {
        val queue = EventQueue(totalCostLimit = 10, exporterCostLimit = 10)

        queue.send(TestPayload(timestamp = 1L, exporterClass = ExporterA::class.java, payloadCost = 1))
        queue.send(TestPayload(timestamp = 2L, exporterClass = ExporterA::class.java, payloadCost = 1))

        queue.removeFirst(ExporterA::class.java, 1)

        val remaining = queue.earliest(costBudget = 10, limit = 10, except = emptySet())
        assertEquals(1, remaining?.items?.size)
        assertEquals(2L, remaining?.items?.first()?.timestamp)

        queue.removeFirst(ExporterA::class.java, 5)

        assertNull(queue.earliest(costBudget = 10, limit = 10, except = emptySet()))
    }

    private class ExporterA : EventExporting {
        override suspend fun export(items: List<EventQueueItem>) = Unit
    }

    private class ExporterB : EventExporting {
        override suspend fun export(items: List<EventQueueItem>) = Unit
    }

    private data class TestPayload(
        override val timestamp: Long,
        override val exporterClass: Class<out EventExporting>,
        private val payloadCost: Int,
    ) : EventQueueItemPayload {
        override fun cost(): Int = payloadCost
    }
}
