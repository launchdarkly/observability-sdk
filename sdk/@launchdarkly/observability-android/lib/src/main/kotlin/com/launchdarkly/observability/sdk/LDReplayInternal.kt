package com.launchdarkly.observability.sdk

import android.app.Activity
import com.launchdarkly.observability.replay.plugin.SessionReplayHookProxy

/**
 * Internal singleton that owns the mutable state backing [LDReplay] and exposes
 * APIs intended for use by other components of the observability SDK only
 * (the Observability plugin, Session Replay, and the .NET MAUI bridge layer).
 *
 * This object is `public` (not `internal`) because it is consumed across Gradle
 * modules — for example by the `.NET` MAUI bridge — but it is **not** part of
 * the customer-facing API. Customers should depend on [LDReplay] instead.
 */
object LDReplayInternal {

    /**
     * The currently registered Session Replay implementation, or `null` if
     * Session Replay has not been initialized.
     */
    @Volatile
    internal var client: SessionReplayServicing? = null

    /**
     * Hook proxy for the C# / MAUI bridge.
     */
    @Volatile
    val hookProxy: SessionReplayHookProxy?
        get() = client?.let { SessionReplayHookProxy(it) }

    /**
     * Active [SessionReplayServicing] implementation that the [LDReplay]
     * singleton forwards to. Defaults to a no-op so calls made before
     * initialization are safe.
     */
    @Volatile
    internal var delegate: SessionReplayServicing = NoOpSessionReplay
        private set

    /**
     * Wires [LDReplay] to the active Session Replay controller.
     */
    internal fun init(controller: SessionReplayServicing) {
        delegate = controller
        client = controller
    }

    /**
     * Registers [activity] for touch capture.
     *
     * You do not normally need to call this. It is only necessary when the SDK
     * is initialized after the activity has already started (e.g. in React
     * Native, where the host activity is already running before the SDK
     * initializes).
     */
    fun registerActivity(activity: Activity) {
        delegate.registerActivity(activity)
    }
}

private object NoOpSessionReplay : SessionReplayServicing {
    override fun start() {}
    override fun stop() {}
    override fun flush() {}
    override fun afterIdentify(
        contextKeys: Map<String, String>,
        canonicalKey: String,
        completed: Boolean
    ) {
    }
}

internal interface SessionReplayServicing {
    fun start()
    fun stop()
    fun flush()
    fun afterIdentify(contextKeys: Map<String, String>, canonicalKey: String, completed: Boolean)
    fun registerActivity(activity: Activity) {}
}
