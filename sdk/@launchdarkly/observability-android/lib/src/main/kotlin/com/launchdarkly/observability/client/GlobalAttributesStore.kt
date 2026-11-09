package com.launchdarkly.observability.client

import com.launchdarkly.observability.context.LDObserveLogging
import com.launchdarkly.observability.context.ObserveLogger
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.common.AttributesBuilder
import java.util.concurrent.atomic.AtomicReference

/**
 * Process-wide, in-memory store behind [com.launchdarkly.observability.interfaces.Observe.setAttributes]
 * and [com.launchdarkly.observability.interfaces.Observe.removeAttributes].
 *
 * It is a singleton (rather than state on [ObservabilityService]) so calls made before the SDK is
 * initialized still take effect: the global-attribute processors and metric recorders read it once
 * the pipeline exists. Values are not persisted and survive session rotation.
 *
 * Reads are a lock-free load of an immutable [Attributes] snapshot; writers serialize on a lock and
 * publish a rebuilt snapshot. Keys are matched by name, so re-setting a key with a different value
 * type replaces it.
 */
internal object GlobalAttributesStore {

    /**
     * Keys owned by the SDK's session management. Globals must never shadow or spoof them.
     */
    val RESERVED_KEYS: Set<String> = setOf("session.id", "highlight.session_id")

    private val snapshot = AtomicReference(Attributes.empty())
    private val writeLock = Any()

    /**
     * Logger used to report ignored reserved keys. Replaced with the configured SDK logger when
     * [ObservabilityService] is created; until then it falls back to the default Android logger.
     */
    @Volatile
    var logger: ObserveLogger? = null

    fun current(): Attributes = snapshot.get()

    fun set(attributes: Attributes) {
        val accepted = LinkedHashMap<String, Pair<AttributeKey<*>, Any>>()
        for ((key, value) in attributes.asMap()) {
            if (key.key in RESERVED_KEYS) {
                resolveLogger().warn("Ignoring reserved global attribute key '${key.key}'")
                continue
            }
            accepted[key.key] = key to value
        }
        if (accepted.isEmpty()) return

        synchronized(writeLock) {
            val builder = Attributes.builder()
            for ((key, value) in snapshot.get().asMap()) {
                if (key.key !in accepted) builder.putUnchecked(key, value)
            }
            for ((key, value) in accepted.values) {
                builder.putUnchecked(key, value)
            }
            snapshot.set(builder.build())
        }
    }

    fun remove(keys: Collection<String>) {
        if (keys.isEmpty()) return
        val toRemove = keys.toHashSet()

        synchronized(writeLock) {
            val current = snapshot.get()
            if (current.asMap().keys.none { it.key in toRemove }) return
            val builder = Attributes.builder()
            for ((key, value) in current.asMap()) {
                if (key.key !in toRemove) builder.putUnchecked(key, value)
            }
            snapshot.set(builder.build())
        }
    }

    /**
     * Returns the current globals overlaid with [explicit]. Explicit values win for any key name
     * they define, matching the span/log precedence where globals only fill missing keys.
     */
    fun merge(explicit: Attributes): Attributes {
        val globals = snapshot.get()
        if (globals.isEmpty) return explicit
        if (explicit.isEmpty) return globals

        val explicitKeys = explicit.asMap().keys.mapTo(HashSet()) { it.key }
        val builder = Attributes.builder()
        for ((key, value) in globals.asMap()) {
            if (key.key !in explicitKeys) builder.putUnchecked(key, value)
        }
        builder.putAll(explicit)
        return builder.build()
    }

    /** Test hook: drops every global attribute. */
    internal fun clear() {
        synchronized(writeLock) {
            snapshot.set(Attributes.empty())
        }
    }

    private fun resolveLogger(): ObserveLogger =
        logger ?: ObserveLogger.build(LDObserveLogging.adapter(), DEFAULT_LOGGER_NAME, debug = false).also { logger = it }

    private const val DEFAULT_LOGGER_NAME = "LaunchDarklyObservabilityPlugin"
}

@Suppress("UNCHECKED_CAST")
internal fun AttributesBuilder.putUnchecked(key: AttributeKey<*>, value: Any): AttributesBuilder =
    put(key as AttributeKey<Any>, value)
