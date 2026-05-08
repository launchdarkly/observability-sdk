package com.launchdarkly.observability.sdk

import android.app.Activity
import com.launchdarkly.observability.util.runOnMainThread

/**
 * Captures [LDReplay] calls made before the live replay service is wired up, then replays
 * them onto the service in [bind]. Without this, calls during the init race (e.g. an
 * `LDReplay.start()` from `Application.onCreate` while `LDObserve.init(...)` is still
 * dispatching to the main looper) would silently no-op.
 *
 * Buffered state:
 *  - [liveReplayService]: the live replay service once [bind] has run; `null` doubles as the
 *    "pre-init" flag.
 *  - `pendingEnabled`: latest pre-init `isEnabled` write. `null` means "no buffered value",
 *    so [bind] won't clobber the replay service's `ReplayOptions.enabled` default.
 *  - `pendingActivities`: every pre-init [registerActivity], in submission order.
 *  - `pendingIdentify`: most recent pre-init [afterIdentify] only — older identifies are stale.
 *
 * Concurrency:
 *  - [isEnabled] reads hold the private monitor so they observe a consistent snapshot of
 *    ([liveReplayService], `pendingEnabled`). Reading the two `@Volatile` fields lock-free
 *    is unsafe during [bind]: a reader can read `liveReplayService = null` (stale) and then
 *    `pendingEnabled = null` (cleared by `bind`), reporting `false` even when the buffered
 *    `true` was already applied to the live service. JMM happens-before flows forward from
 *    the later volatile read, not backward to the earlier one.
 *  - Setters and [bind] hold the same monitor so the setters' "check [liveReplayService],
 *    then write buffer" can't race [bind]'s publish + clear.
 *  - [bind] writes in the order: apply buffers → publish [liveReplayService] → clear buffers,
 *    so single-field readers of [liveReplayService] (e.g. consumers of the public getter)
 *    that observe the live service can rely on its state already being correct.
 *  - [runOnMainThread] dispatch happens outside the lock to avoid blocking on the main looper
 *    while the monitor is held.
 */
internal class PreInitReplayBuffer {
    @Volatile
    var liveReplayService: SessionReplayServicing? = null
        private set

    @Volatile
    private var pendingEnabled: Boolean? = null

    private val pendingActivities = mutableListOf<Activity>()
    private var pendingIdentify: PendingIdentify? = null

    val isEnabled: Boolean
        get() {
            // Snapshot both fields under the same monitor [bind] uses, otherwise a reader can
            // observe the dual-field tear documented in the class header and return `false`
            // mid-bind. The live service's `isEnabled` itself is read outside the lock —
            // the lock only needs to protect the (liveReplayService, pendingEnabled) pair.
            val service = synchronized(this) {
                liveReplayService ?: return pendingEnabled ?: false
            }
            return service.isEnabled
        }

    fun setEnabled(value: Boolean) {
        val target: SessionReplayServicing? = synchronized(this) {
            val current = liveReplayService
            if (current == null) {
                pendingEnabled = value
                null
            } else {
                current
            }
        }
        target?.let { runOnMainThread { it.isEnabled = value } }
    }

    fun registerActivity(activity: Activity) {
        val target: SessionReplayServicing? = synchronized(this) {
            val current = liveReplayService
            if (current == null) {
                pendingActivities.add(activity)
                null
            } else {
                current
            }
        }
        target?.let { runOnMainThread { it.registerActivity(activity) } }
    }

    fun afterIdentify(contextKeys: Map<String, String>, canonicalKey: String, completed: Boolean) {
        val target: SessionReplayServicing? = synchronized(this) {
            val current = liveReplayService
            if (current == null) {
                // Defensive copy of [contextKeys] in case the caller mutates the map afterwards.
                pendingIdentify = PendingIdentify(contextKeys.toMap(), canonicalKey, completed)
                null
            } else {
                current
            }
        }
        target?.let {
            runOnMainThread { it.afterIdentify(contextKeys, canonicalKey, completed) }
        }
    }

    fun flush() {
        val current = liveReplayService ?: return
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

            // Publish [liveReplayService] before clearing buffers so a reader observing a
            // cleared buffer is guaranteed to also see the live service (per JMM volatile
            // ordering).
            liveReplayService = replayService
            pendingEnabled = null
            pendingActivities.clear()
            pendingIdentify = null
        }
    }

    fun reset() {
        synchronized(this) {
            liveReplayService = null
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
