package com.launchdarkly.observability.utils

import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

/**
 * Thread-safe bounded map with FIFO eviction.
 *
 * Insertions beyond [capacity] evict the oldest key/value pair.
 * All mutations are atomic under a write lock.
 */
internal class BoundedMap<K, V>(private val capacity: Int) {

    private val storage = LinkedHashMap<K, V>(capacity, 0.75f, false)
    private val lock = ReentrantReadWriteLock()

    /**
     * Atomically sets a value and optionally evicts the oldest entry.
     *
     * @return the evicted value if capacity overflow occurred, otherwise null.
     */
    fun put(key: K, value: V): V? {
        lock.write {
            storage.remove(key)
            storage[key] = value

            if (storage.size > capacity) {
                val oldestKey = storage.keys.first()
                return storage.remove(oldestKey)
            }
            return null
        }
    }

    /**
     * Atomically removes and returns the value for [key], or null if absent.
     */
    fun remove(key: K): V? {
        lock.write {
            return storage.remove(key)
        }
    }

    val size: Int
        get() = lock.read { storage.size }
}