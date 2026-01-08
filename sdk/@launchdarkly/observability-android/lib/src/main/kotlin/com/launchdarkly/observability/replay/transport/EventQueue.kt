package com.launchdarkly.observability.replay.transport

internal class EventQueue(
    private val limitSize: Int = DEFAULT_LIMIT_SIZE,
    private val exporterLimitSize: Int = DEFAULT_EXPORTER_LIMIT_SIZE,
) {
    data class EarliestItemsResult(
        val exporterClass: Class<out EventExporting>,
        val items: List<EventQueueItem>,
        val cost: Int,
    )

    private data class ExporterState(
        var size: Int = 0,
    )

    private val lock = Any()
    private val storage = mutableMapOf<Class<out EventExporting>, ArrayDeque<EventQueueItem>>()
    private val currentSizes = mutableMapOf<Class<out EventExporting>, ExporterState>()
    private var currentSize = 0

    fun isFull(): Boolean = synchronized(lock) {
        currentSize >= limitSize
    }

    fun send(payload: EventQueueItemPayload) {
        send(EventQueueItem(payload))
    }

    fun send(payloads: List<EventQueueItemPayload>) {
        payloads.forEach { send(it) }
    }

    fun earliest(
        costBudget: Int,
        limit: Int,
        except: Set<Class<out EventExporting>>,
    ): EarliestItemsResult? = synchronized(lock) {
        var earliest: Pair<Class<out EventExporting>, ArrayDeque<EventQueueItem>>? = null
        var earliestTimestamp: Long? = null

        for ((exporterClass, items) in storage) {
            if (exporterClass in except) continue
            val firstItem = items.firstOrNull() ?: continue
            val timestamp = firstItem.timestamp
            if (earliestTimestamp != null && timestamp >= earliestTimestamp) continue
            earliestTimestamp = timestamp
            earliest = exporterClass to items
        }

        val selected = earliest ?: return@synchronized null
        val items = takeFirstItems(selected.second, costBudget, limit)
        if (items.isEmpty()) return@synchronized null
        val cost = items.sumOf { it.cost }
        EarliestItemsResult(
            exporterClass = selected.first,
            items = items,
            cost = cost,
        )
    }

    fun removeFirst(exporterClass: Class<out EventExporting>, count: Int) {
        synchronized(lock) {
            val items = storage[exporterClass] ?: return
            if (count <= 0) return

            val removeCount = minOf(count, items.size)
            var removedCost = 0
            repeat(removeCount) {
                removedCost += items.removeFirst().cost
            }

            currentSize = (currentSize - removedCost).coerceAtLeast(0)
            val state = currentSizes.getOrPut(exporterClass) { ExporterState() }
            state.size = (state.size - removedCost).coerceAtLeast(0)

            if (items.isEmpty()) {
                storage.remove(exporterClass)
            }
        }
    }

    private fun send(item: EventQueueItem) {
        synchronized(lock) {
            if (currentSize != 0 && currentSize + item.cost > limitSize) {
                return
            }

            val state = currentSizes.getOrPut(item.exporterClass) { ExporterState() }
            if (state.size + item.cost > exporterLimitSize) {
                return
            }

            storage.getOrPut(item.exporterClass) { ArrayDeque() }.addLast(item)
            currentSize += item.cost
            state.size += item.cost
        }
    }

    private fun takeFirstItems(
        items: ArrayDeque<EventQueueItem>,
        costBudget: Int,
        limit: Int,
    ): List<EventQueueItem> {
        if (items.isEmpty() || limit <= 0) return emptyList()
        val result = ArrayList<EventQueueItem>(minOf(items.size, limit))
        var sumCost = 0

        for (item in items) {
            result.add(item)
            sumCost += item.cost
            if (result.size >= limit || sumCost > costBudget) {
                break
            }
        }

        return result
    }

    private companion object {
        private const val DEFAULT_LIMIT_SIZE = 5_000_000
        private const val DEFAULT_EXPORTER_LIMIT_SIZE = 2_500_000
    }
}
