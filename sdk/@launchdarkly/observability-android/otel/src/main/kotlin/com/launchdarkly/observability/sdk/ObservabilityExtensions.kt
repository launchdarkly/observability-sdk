package com.launchdarkly.observability.sdk

import com.launchdarkly.observability.InternalObservabilityApi
import com.launchdarkly.observability.api.ObservabilityOptions
import com.launchdarkly.observability.client.ObservabilityInstrumenting
import com.launchdarkly.observability.context.ObserveLogger

/**
 * Everything the full observability artifact contributes to the standalone [LDObserve.init] path.
 *
 * The plugin path does not need this: `Observability` and `SessionReplay` live in the full artifact
 * and wire themselves up directly. Standalone init is different — the caller names only
 * [LDObserve], which lives in the OTel-only core, so the core has to discover at runtime whether
 * the full artifact is present.
 */
interface ObservabilityExtensions {
    /** Automatic instrumentation for a standalone-initialized service. */
    fun createInstrumentation(
        sdkKey: String,
        options: ObservabilityOptions,
        logger: ObserveLogger,
    ): ObservabilityInstrumenting

    /** Installs Session Replay when [LDObserve.init] is given a replay configuration. */
    val sessionReplayInstaller: SessionReplayInstalling
}

/**
 * Locates [ObservabilityExtensions] by name, so the core can stay free of any reference to the full
 * artifact's types.
 *
 * Resolution is attempted once and the outcome (including absence) is cached. Absence is the normal
 * state for `com.launchdarkly:launchdarkly-otel-android` on its own and is not an error.
 *
 * The implementation class is kept by the full artifact's consumer ProGuard rules, since nothing
 * references it statically.
 */
@InternalObservabilityApi
object ObservabilityExtensionsLoader {
    private const val IMPLEMENTATION_CLASS =
        "com.launchdarkly.observability.sdk.FullObservabilityExtensions"

    @Volatile
    private var resolved = false

    @Volatile
    private var extensions: ObservabilityExtensions? = null

    @Synchronized
    fun load(): ObservabilityExtensions? {
        if (resolved) return extensions
        resolved = true
        extensions = try {
            Class.forName(IMPLEMENTATION_CLASS)
                .getDeclaredConstructor()
                .newInstance() as ObservabilityExtensions
        } catch (_: ClassNotFoundException) {
            null
        } catch (_: ReflectiveOperationException) {
            null
        }
        return extensions
    }
}
