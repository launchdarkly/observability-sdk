package com.launchdarkly.observability.sdk

import android.app.Activity
import com.launchdarkly.observability.util.runOnMainThread

/**
 * Buffers caller intent that arrives before [LDReplay] has been wired up to a live replay
 * service, then drains the buffer onto the replay service during [bind] without losing
 * writes to the init race.
 *
 * The buffering is the whole reason this class exists: callers may set [LDReplay.isEnabled],
 * register activities, or record identify completions before the SDK has finished initializing
 * (e.g. `Application.onCreate` fires `LDReplay.start()` while `LDObserve.init(...)` is still
 * dispatching the replay service setup to the main looper). Without this buffer, those calls
 * would silently no-op against the placeholder; here, they're captured and replayed during
 * [bind].
 *
 * The class also routes post-init calls to the live replay service, but that's plumbing —
 * the distinguishing trait is the safe pre-init capture.
 *
 * Buffered state:
 *  - [client]: the wired live replay service, or `null` before [bind] is called. Doubles as
 *    the "are we past init?" flag.
 *  - `pendingEnabled`: latest pre-init [LDReplay.isEnabled] write. `null` means "no value
 *    buffered" — distinct from `false`, which lets [bind] avoid clobbering the replay
 *    service's `ReplayOptions.enabled`-derived default.
 *  - `pendingActivities`: every pre-init [registerActivity] call, in submission order. All
 *    are replayed because each activity is independently meaningful.
 *  - `pendingIdentify`: most recent pre-init [afterIdentify] call. Older identifies are stale
 *    by definition, so we keep "latest wins" semantics rather than a queue.
 *
 * Concurrency contract (the part that actually keeps writes from being lost during init):
 *  - Reads of [isEnabled] are wait-free; the field is `@Volatile` and the read does not
 *    take the lock. Other buffers are not exposed for unlocked reading.
 *  - All mutations ([setEnabled], [registerActivity], [afterIdentify], [bind], [reset]) hold
 *    a private monitor lock so the "check `client`, then write buffer" pattern in the setters
 *    cannot interleave with [bind] publishing a replay service and clearing buffers (the
 *    lost-write race).
 *  - [bind]'s write order — apply → publish `client` → clear buffers — guarantees that any
 *    reader observing a cleared `pendingEnabled` is also observing the published `client`
 *    (per the JMM's volatile happens-before / synchronization-order rule: a volatile read
 *    sees the last write in synchronization order, and synchronization order is consistent
 *    with program order).
 *  - Setter [runOnMainThread] dispatches happen *outside* the lock so we don't hold the
 *    monitor while blocking on the main looper.
 *
 * Visibility: `internal` because [LDReplay] (in this same package) needs to instantiate it,
 * but no consumer outside the SDK module should depend on it. Tests within the module can
 * exercise it directly if needed; production code should go through [LDReplay].
 */
internal class PreInitReplayBuffer {
    @Volatile
    var client: SessionReplayServicing? = null
        private set

    @Volatile
    private var pendingEnabled: Boolean? = null

    private val pendingActivities = mutableListOf<Activity>()
    private var pendingIdentify: PendingIdentify? = null

    val isEnabled: Boolean
        get() = client?.isEnabled ?: pendingEnabled ?: false

    fun setEnabled(value: Boolean) {
        val replayServiceToForward: SessionReplayServicing? = synchronized(this) {
            val current = client
            if (current == null) {
                pendingEnabled = value
                null
            } else {
                current
            }
        }
        replayServiceToForward?.let { runOnMainThread { it.isEnabled = value } }
    }

    fun registerActivity(activity: Activity) {
        val replayServiceToForward: SessionReplayServicing? = synchronized(this) {
            val current = client
            if (current == null) {
                pendingActivities.add(activity)
                null
            } else {
                current
            }
        }
        replayServiceToForward?.let { runOnMainThread { it.registerActivity(activity) } }
    }

    fun afterIdentify(contextKeys: Map<String, String>, canonicalKey: String, completed: Boolean) {
        val replayServiceToForward: SessionReplayServicing? = synchronized(this) {
            val current = client
            if (current == null) {
                // Defensive copy of [contextKeys] in case the caller mutates the map afterwards.
                pendingIdentify = PendingIdentify(contextKeys.toMap(), canonicalKey, completed)
                null
            } else {
                current
            }
        }
        replayServiceToForward?.let {
            runOnMainThread { it.afterIdentify(contextKeys, canonicalKey, completed) }
        }
    }

    fun flush() {
        val current = client ?: return
        runOnMainThread { current.flush() }
    }

    fun bind(replayService: SessionReplayServicing) {
        synchronized(this) {
            // Drain buffered state into the live replay service in a deterministic order:
            // enable first (so subsequent operations see the right gate), then activities,
            // then the latest identify.
            pendingEnabled?.let { replayService.isEnabled = it }
            pendingActivities.forEach { replayService.registerActivity(it) }
            pendingIdentify?.let { replayService.afterIdentify(it.contextKeys, it.canonicalKey, it.completed) }

            // Publish `client` before clearing buffers so a reader observing a cleared
            // buffer is guaranteed to also see the live client (per JMM volatile ordering).
            client = replayService
            pendingEnabled = null
            pendingActivities.clear()
            pendingIdentify = null
        }
    }

    fun reset() {
        synchronized(this) {
            client = null
            pendingEnabled = null
            pendingActivities.clear()
            pendingIdentify = null
        }
    }

    private data class PendingIdentify(
        val contextKeys: Map<String, String>,
        val canonicalKey: String,
        val completed: Boolean,
    )
}
