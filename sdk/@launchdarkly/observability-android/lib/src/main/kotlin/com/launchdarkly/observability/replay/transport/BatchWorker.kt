package com.launchdarkly.observability.replay.transport

import com.launchdarkly.logging.LDLogger
import com.launchdarkly.observability.coroutines.DispatcherProvider
import com.launchdarkly.observability.coroutines.DispatcherProviderHolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlin.math.pow
import kotlin.math.min
import kotlin.random.Random

internal class BatchWorker(
    private val eventQueue: EventQueue,
    private val logger: LDLogger,
    dispatcherProvider: DispatcherProvider = DispatcherProviderHolder.current,
) {
    private val scope = CoroutineScope(dispatcherProvider.default + SupervisorJob())
    private val ioDispatcher = dispatcherProvider.io
    private val lock = Any()
    private val exporters = mutableMapOf<Class<out EventExporting>, EventExporting>()
    private val exportersInFlight = mutableSetOf<Class<out EventExporting>>()
    private val exporterBackoff = mutableMapOf<Class<out EventExporting>, BackoffInfo>()
    private var costInFlight = 0
    private var flushableWorker: FlushableWorker? = null

    fun addExporter(exporter: EventExporting) {
        synchronized(lock) {
            exporters[exporter.javaClass] = exporter
        }
    }

    fun start() {
        logger.info("$LOG_TAG start")
        if (flushableWorker == null) {
            flushableWorker = FlushableWorker(
                intervalMillis = INTERVAL_MS,
                scope = scope,
                work = ::sendQueueItems,
            )
        }
        flushableWorker?.start()
    }

    fun stop() {
        logger.info("$LOG_TAG stop")
        flushableWorker?.stop()
    }

    fun flush() {
        logger.info("$LOG_TAG flush")
        flushableWorker?.flush()
    }

    private suspend fun sendQueueItems(isFlushing: Boolean) {
        while (true) {
            val (budget, remainingSlots, except, inFlight) = snapshotState()

            if (remainingSlots <= 0 && !isFlushing) break
            if (inFlight != 0 && budget <= 0 && !isFlushing) break

            val earliest = eventQueue.earliest(
                costBudget = budget,
                limit = MAX_CONCURRENT_ITEMS,
                except = except,
            ) ?: break

            val exporter = synchronized(lock) { exporters[earliest.exporterClass] }
            if (exporter == null) {
                logger.error("Dropping ${earliest.items.size} items: exporter not found for ${earliest.exporterClass.name}")
                eventQueue.removeFirst(earliest.exporterClass, earliest.items.size)
                continue
            }

            if (tryReserve(earliest.exporterClass, earliest.cost)) {
                scope.launch(ioDispatcher) {
                    logger.debug("$LOG_TAG export start: exporter=${earliest.exporterClass.name} items=${earliest.items.size} cost=${earliest.cost} flush=$isFlushing")
                    try {
                        exporter.export(earliest.items)
                        finishExport(earliest.exporterClass, earliest.items.size, earliest.cost, null)
                    } catch (e: Exception) {
                        finishExport(earliest.exporterClass, earliest.items.size, earliest.cost, e)
                    }
                }
            }
        }
    }

    private fun snapshotState(): Snapshot {
        val now = System.currentTimeMillis()
        synchronized(lock) {
            val remainingSlots = MAX_CONCURRENT_EXPORTERS - exportersInFlight.size
            val budget = MAX_CONCURRENT_COST - costInFlight
            val except = exportersInFlight.toMutableSet()
            exporterBackoff.forEach { (exporterClass, info) ->
                if (info.untilMillis > now) {
                    except.add(exporterClass)
                }
            }
            return Snapshot(
                budget = budget,
                remainingSlots = remainingSlots,
                except = except,
                costInFlight = costInFlight,
            )
        }
    }

    private fun tryReserve(exporterClass: Class<out EventExporting>, cost: Int): Boolean {
        synchronized(lock) {
            if (exportersInFlight.contains(exporterClass)) {
                return false
            }
            exportersInFlight.add(exporterClass)
            costInFlight += cost
            return true
        }
    }

    private fun finishExport(
        exporterClass: Class<out EventExporting>,
        itemsCount: Int,
        cost: Int,
        error: Exception?,
    ) {
        if (error == null) {
            eventQueue.removeFirst(exporterClass, itemsCount)
            logger.debug("$LOG_TAG export success: exporter=${exporterClass.name} items=$itemsCount cost=$cost")
        } else {
            logger.error("$LOG_TAG export failed: exporter=${exporterClass.name} items=$itemsCount cost=$cost error=${error.message}", error)
        }

        synchronized(lock) {
            if (error != null) {
                val attempts = (exporterBackoff[exporterClass]?.attempts ?: 0) + 1
                val backoff = min(
                    BASE_BACKOFF_SECONDS * 2.0.pow(maxOf(0, attempts - 1).toDouble()),
                    MAX_BACKOFF_SECONDS,
                )
                val jitter = backoff * BACKOFF_JITTER
                val jittered = (backoff + Random.nextDouble(-jitter, jitter)).coerceAtLeast(0.0)
                val until = System.currentTimeMillis() + (jittered * 1000).toLong()
                exporterBackoff[exporterClass] = BackoffInfo(untilMillis = until, attempts = attempts)
            } else {
                exporterBackoff.remove(exporterClass)
            }

            exportersInFlight.remove(exporterClass)
            costInFlight = (costInFlight - cost).coerceAtLeast(0)
        }
    }

    private data class Snapshot(
        val budget: Int,
        val remainingSlots: Int,
        val except: Set<Class<out EventExporting>>,
        val costInFlight: Int,
    )

    private data class BackoffInfo(
        val untilMillis: Long,
        val attempts: Int,
    )

    private companion object {
        private const val LOG_TAG = "BatchWorker"
        private const val MAX_CONCURRENT_COST = 30_000
        private const val MAX_CONCURRENT_ITEMS = 100
        private const val MAX_CONCURRENT_EXPORTERS = 3
        private const val BASE_BACKOFF_SECONDS = 2.0
        private const val MAX_BACKOFF_SECONDS = 60.0
        private const val BACKOFF_JITTER = 0.2
        private const val INTERVAL_MS = 1500L
    }
}
