package com.launchdarkly.observability.plugin

import com.launchdarkly.sdk.android.LDClient
import java.util.WeakHashMap

/**
 * Manages a collection of instrumentation contributors associated with an [com.launchdarkly.sdk.android.LDClient] instance.
 *
 * This object provides a central place to register and retrieve instrumentation contributors for a given LDClient.
 * It uses a [java.util.WeakHashMap] to store contributors, which allows the [com.launchdarkly.sdk.android.LDClient] instances and
 * associated contributors to be garbage collected when they are no longer in use.
 */
internal object InstrumentationContributorManager {
    private val contributors = WeakHashMap<LDClient, MutableList<InstrumentationContributor>>()

    /**
     * Adds a [InstrumentationContributor] to the list of contributors associated with the given [LDClient].
     *
     * If no contributors have been added for the client before, a new list is created.
     *
     * @param client The [LDClient] to associate the contributor with.
     * @param contributor The [InstrumentationContributor] to add.
     */
    fun add(client: LDClient, contributor: InstrumentationContributor) {
        synchronized(contributors) {
            contributors.getOrPut(client) { mutableListOf() }.add(contributor)
        }
    }

    /**
     * Retrieves a list of [InstrumentationContributor]s associated with the given [LDClient].
     *
     * The returned list is a snapshot and is safe to iterate over.
     *
     * @param client The [LDClient] to get the contributors for.
     * @return A list of [InstrumentationContributor]s, or empty if no contributors are associated with the client.
     */
    fun get(client: LDClient): List<InstrumentationContributor> = synchronized(contributors) {
        contributors[client]?.toList().orEmpty()
    }

    /**
     * Clears all contributor registrations.
     */
    fun reset() = synchronized(contributors) {
        contributors.clear()
    }
}
