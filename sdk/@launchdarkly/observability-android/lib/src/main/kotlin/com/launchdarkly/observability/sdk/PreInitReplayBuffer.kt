package com.launchdarkly.observability.sdk

import android.app.Activity
import com.launchdarkly.observability.replay.SessionReplayStartResult
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
 *  - `pendingEnable`: latest pre-init `isEnabled` write or [start]. `null` means "no buffered
 *    value", so [bind] won't clobber the replay service's `ReplayOptions.enabled` default.
 *  - `pendingActivities`: every pre-init [registerActivity], in submission order.
 *  - `pendingIdentify`: most recent pre-init [afterIdentify] only — older identifies are stale.
 *
 * Concurrency:
 *  - [isEnabled] reads hold the private monitor so they observe a consistent snapshot of
 *    ([liveReplayService], `pendingEnable`). Reading the two `@Volatile` fields lock-free
 *    is unsafe during [bind]: a reader can read `liveReplayService = null` (stale) and then
 *    `pendingEnable = null` (cleared by `bind`), reporting `false` even when the buffered
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
    private var pendingEnable: PendingEnable? = null

    private val pendingActivities = mutableListOf<Activity>()
    private var pendingIdentify: PendingIdentify? = null

    val isEnabled: Boolean
        get() {
            // Snapshot both fields under the same monitor [bind] uses, otherwise a reader can
            // observe the dual-field tear documented in the class header and return `false`
            // mid-bind. The live service's `isEnabled` itself is read outside the lock —
            // the lock only needs to protect the (liveReplayService, pendingEnable) pair.
            val service = synchronized(this) {
                liveReplayService ?: return pendingEnable?.isEnabled ?: false
            }
            return service.isEnabled
        }

    fun setEnabled(value: Boolean) {
        val target: SessionReplayServicing? = synchronized(this) {
            val current = liveReplayService
            if (current == null) {
                pendingEnable = if (value) PendingEnable.Start(ignoreSampling = false) else PendingEnable.Stop
                null
            } else {
                current
            }
        }
        target?.let { runOnMainThread { it.isEnabled = value } }
    }

    fun start(ignoreSampling: Boolean): SessionReplayStartResult {
        val target: SessionReplayServicing = synchronized(this) {
            val current = liveReplayService
            if (current == null) {
                // The start is applied during [bind]; its outcome cannot be known until then.
                pendingEnable = PendingEnable.Start(ignoreSampling)
                return SessionReplayStartResult.UNAVAILABLE
            }
            current
        }
        // Dispatched outside the monitor, like every other forwarding path here: [runOnMainThread]
        // blocks until the block completes, so holding the lock across it could deadlock against a
        // main thread waiting to enter [bind]. The latch it waits on also publishes `result`.
        var result = SessionReplayStartResult.UNAVAILABLE
        runOnMainThread { result = target.start(ignoreSampling) }
        return result
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
            when (val pending = pendingEnable) {
                // Routed through `start` rather than `isEnabled` so a buffered
                // `LDReplay.start(ignoreSampling = true)` still forces recording here.
                is PendingEnable.Start -> replayService.start(pending.ignoreSampling)
                PendingEnable.Stop -> replayService.isEnabled = false
                null -> Unit
            }
            pendingActivities.forEach { replayService.registerActivity(it) }
            pendingIdentify?.let { replayService.afterIdentify(it.contextKeys, it.canonicalKey, it.completed) }

            // Publish [liveReplayService] before clearing buffers so a reader observing a
            // cleared buffer is guaranteed to also see the live service (per JMM volatile
            // ordering).
            liveReplayService = replayService
            pendingEnable = null
            pendingActivities.clear()
            pendingIdentify = null
        }
    }

    fun reset() {
        synchronized(this) {
            liveReplayService = null
            pendingEnable = null
            pendingActivities.clear()
            pendingIdentify = null
        }
    }

    /**
     * Latest pre-init enable/disable intent. [Start] carries its `ignoreSampling` flag so a
     * pre-init forced start is still forced when [bind] applies it.
     */
    private sealed interface PendingEnable {
        val isEnabled: Boolean

        data class Start(val ignoreSampling: Boolean) : PendingEnable {
            override val isEnabled: Boolean get() = true
        }

        data object Stop : PendingEnable {
            override val isEnabled: Boolean get() = false
        }
    }

    private data class PendingIdentify(
        val contextKeys: Map<String, String>,
        val canonicalKey: String,
        val completed: Boolean,
    )
}
