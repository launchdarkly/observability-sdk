package com.launchdarkly.observability.sdk

import android.app.Activity
import com.launchdarkly.observability.replay.SessionReplayStartResult
import com.launchdarkly.observability.replay.plugin.SessionReplayHookProxy
import io.opentelemetry.api.common.Attributes

/**
 * LDReplay is the singleton entry point for controlling Session Replay capture.
 *
 * If Session Replay is not configured, most methods are no-ops. The exceptions are stateful
 * operations whose data is buffered and replayed onto the live replay service during
 * [init]: [isEnabled], [start], [registerActivity], and [afterIdentify]. This way, callers can
 * configure replay before SDK initialization without losing their preferences.
 *
 * All public operations are thread-safe.
 */
object LDReplay {
    private val state = PreInitReplayBuffer()

    /**
     * Hook proxy for cross-platform bridges (C# / MAUI, React Native, etc.).
     */
    val hookProxy: SessionReplayHookProxy?
        get() = state.liveReplayService?.let { SessionReplayHookProxy(it) }

    /**
     * Whether session replay capture is currently enabled.
     *
     * Setting this before the SDK has wired up the underlying replay service buffers the
     * value and applies it during [init], so the user's preference survives the no-op → live
     * transition. After [init] runs, writes are dispatched to the live replay service on the
     * main thread.
     *
     * Reads return the live replay service's value when available, otherwise the buffered
     * value, otherwise `false`.
     */
    var isEnabled: Boolean
        get() = state.isEnabled
        set(value) {
            state.setEnabled(value)
        }

    /**
     * Starts session replay capture and reports the outcome.
     *
     * Unlike setting [isEnabled] to `true`, this always attempts a start rather than short-circuiting
     * when replay already reports itself enabled. That distinction matters for a session that is
     * enabled but not recording — most commonly one that [com.launchdarkly.observability.replay.ReplayOptions.sampleRate]
     * excluded — which can only be started by passing [ignoreSampling].
     *
     * Calls made before the SDK has wired up the underlying replay service buffer the start (along
     * with [ignoreSampling]) and apply it during [init], and report
     * [SessionReplayStartResult.UNAVAILABLE] because the outcome is not known yet.
     *
     * @param ignoreSampling starts recording even when `sampleRate` would exclude this session.
     *   Intended for debugging: it also overrides a sampling decision already made for the current
     *   enable cycle, which a plain start cannot do until [stop] resets it.
     */
    @JvmOverloads
    fun start(ignoreSampling: Boolean = false): SessionReplayStartResult =
        state.start(ignoreSampling)

    /** Pauses session replay capture. */
    fun stop() {
        isEnabled = false
    }

    /**
     * Flushes any queued replay events immediately. No-op before the SDK is initialized
     * because there are no queued events without a live replay service.
     */
    fun flush() {
        state.flush()
    }

    /**
     * Registers [activity] for touch capture.
     *
     * You do not normally need to call this. It is only necessary when the SDK is initialized
     * after the activity has already started (e.g. in React Native, where the host activity
     * is already running before the SDK initializes).
     *
     * Calls made before [init] are buffered and replayed onto the live replay service during init.
     */
    fun registerActivity(activity: Activity) {
        state.registerActivity(activity)
    }

    /**
     * Records the result of a LaunchDarkly identify so the live replay service can pivot
     * to the new context. Calls made before [init] are buffered; only the most recent identify
     * is replayed during init (older identifies are stale by definition).
     *
     * Internal-only: invoked by SDK-internal hooks ([com.launchdarkly.observability.replay.plugin.SessionReplayHookProxy],
     * [com.launchdarkly.observability.replay.plugin.SessionReplayHook]). Not part of the public surface.
     */
    internal fun afterIdentify(
        contextKeys: Map<String, String>,
        canonicalKey: String,
        completed: Boolean
    ) {
        state.afterIdentify(contextKeys, canonicalKey, completed)
    }

    /**
     * Read-only snapshot of the live replay service (or `null` before [init] has run). Used by
     * SDK-internal call sites such as [com.launchdarkly.observability.replay.plugin.SessionReplayPluginImpl]
     * to detect duplicate registrations. Tests should use [init] / [resetForTest] rather than
     * mutating this directly.
     */
    internal val liveReplayService: SessionReplayServicing?
        get() = state.liveReplayService

    /**
     * Wires LDReplay to the active Session Replay service.
     *
     * Forwards all buffered state — [isEnabled], registered activities, and the most recent
     * identify — to [replayService] so the user's pre-init configuration is preserved across
     * the swap. Buffers are cleared as part of binding.
     */
    internal fun init(replayService: SessionReplayServicing) {
        state.bind(replayService)
    }

    /** Test-only: clears all internal state, including all buffers and the live replay service. */
    internal fun resetForTest() {
        state.reset()
    }
}

internal interface SessionReplayServicing {
    /**
     * Single source of truth for whether replay capture is active. Replaces the previous
     * `start()` / `stop()` pair so callers like [LDReplay] can buffer a pre-init value and
     * forward it to a live replay service during initialization.
     */
    var isEnabled: Boolean

    /**
     * Enables capture and reports the outcome. Unlike assigning [isEnabled], this always attempts
     * a start, so it can recover a session that reports itself enabled but is not recording.
     */
    fun start(ignoreSampling: Boolean = false): SessionReplayStartResult

    fun flush()
    fun afterIdentify(contextKeys: Map<String, String>, canonicalKey: String, completed: Boolean)
    fun afterTrack(name: String, metricValue: Double?, attributes: Attributes)
    fun registerActivity(activity: Activity) {}
}
