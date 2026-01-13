package com.launchdarkly.observability.replay.transport

internal class EventQueue(
    private val totalCostLimit: Int = DEFAULT_TOTAL_COST_LIMIT,
    private val exporterCostLimit: Int = DEFAULT_EXPORTER_COST_LIMIT,
) {
    data class EarliestItemsResult(
        val exporterClass: Class<out EventExporting>,
        val items: List<EventQueueItem>,
        val cost: Int,
    )

    private data class ExporterState(
        var cost: Int = 0,
    )

    private val lock = Any()
    private val storage = mutableMapOf<Class<out EventExporting>, ArrayDeque<EventQueueItem>>()
    private val currentExporterCosts = mutableMapOf<Class<out EventExporting>, ExporterState>()
    private var currentCost = 0

    fun isFull(): Boolean = synchronized(lock) {
        currentCost >= totalCostLimit
    }

    fun send(payload: EventQueueItemPayload) {
        synchronized(lock) {
            sendLocked(EventQueueItem(payload))
        }
    }

    fun send(payloads: List<EventQueueItemPayload>) {
        synchronized(lock) {
            payloads.forEach { sendLocked(EventQueueItem(it)) }
        }
    }

    /**
     * Retrieves the earliest batch of items from the event queue.
     *
     * This function identifies the sub-queue (grouped by exporter class) that contains the oldest event.
     * From that sub-queue, it takes a batch of items that fits within the specified `costBudget` and `limit`.
     *
     * @param costBudget The maximum total cost of items to be returned. The batch size will be constrained by this value.
     * @param limit The maximum number of items to return.
     * @param except A set of exporter classes to exclude from the search. This is used to prevent retrying a batch for an exporter that is
     *   currently being processed or has recently failed.
     *
     * @return An [EarliestItemsResult] containing the exporter class, the list of items, and their
     *   total cost, or `null` if the queue is empty or no suitable items are found.
     */
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

    /**
     * Removes the first [count] items from the queue for a given [exporterClass].
     *
     * This method is thread-safe. It will remove up to `count` items from the beginning of the
     * deque associated with the specified exporter. It then updates the total cost of the queue
     * and the cost specific to that exporter. If removing these items results in the exporter's
     * queue becoming empty, the exporter is removed from the internal storage.
     *
     * @param exporterClass The class of the exporter whose items should be removed.
     * @param count The maximum number of items to remove from the front of the queue.
     */
    fun removeFirst(exporterClass: Class<out EventExporting>, count: Int) {
        synchronized(lock) {
            val items = storage[exporterClass] ?: return
            if (count <= 0) return

            val removeCount = minOf(count, items.size)
            var removedCost = 0
            repeat(removeCount) {
                removedCost += items.removeFirst().cost
            }

            currentCost = (currentCost - removedCost).coerceAtLeast(0)
            val state = currentExporterCosts.getOrPut(exporterClass) { ExporterState() }
            state.cost = (state.cost - removedCost).coerceAtLeast(0)

            if (items.isEmpty()) {
                storage.remove(exporterClass)
            }
        }
    }

    /**
     * This method is not thread-safe and must be called within a `synchronized(lock)` block.
     *
     * We don't use a synchronized block inside this method to avoid the overhead of acquiring
     * the lock multiple times when it is called from `send(payloads: List<EventQueueItemPayload>)`.
     *
     * @param item The event item to add to the queue.
     */
    private fun sendLocked(item: EventQueueItem) {
        if (currentCost != 0 && currentCost + item.cost > totalCostLimit) return

        val state = currentExporterCosts.getOrPut(item.exporterClass) { ExporterState() }
        if (state.cost + item.cost > exporterCostLimit) return

        storage.getOrPut(item.exporterClass) { ArrayDeque() }.addLast(item)
        currentCost += item.cost
        state.cost += item.cost
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
        private const val DEFAULT_TOTAL_COST_LIMIT = 5_000_000
        private const val DEFAULT_EXPORTER_COST_LIMIT = 2_500_000
    }
}
