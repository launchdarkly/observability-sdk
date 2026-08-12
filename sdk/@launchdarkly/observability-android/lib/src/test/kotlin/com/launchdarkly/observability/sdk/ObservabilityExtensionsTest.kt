package com.launchdarkly.observability.sdk

import com.launchdarkly.observability.api.ObservabilityOptions
import com.launchdarkly.observability.client.DefaultInstrumentation
import com.launchdarkly.observability.context.ObserveLogger
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

/**
 * The standalone `LDObserve.init` path finds this artifact's instrumentation by class name, because
 * the OTel-only core cannot reference it. Nothing calls [FullObservabilityExtensions] statically, so
 * a rename, a package move, or a missing ProGuard keep rule would silently downgrade standalone init
 * to the OTel-only behaviour instead of failing the build. These tests are the guard.
 */
class ObservabilityExtensionsTest {

    @Test
    fun `the loader finds this artifact's extensions`() {
        assertNotNull(
            ObservabilityExtensionsLoader.load(),
            "FullObservabilityExtensions was not resolvable by name; standalone LDObserve.init " +
                "would silently install no instrumentation and no Session Replay.",
        )
    }

    @Test
    fun `the extensions supply the full product's instrumentation and replay installer`() {
        val extensions = requireNotNull(ObservabilityExtensionsLoader.load())

        val options = ObservabilityOptions()
        val instrumentation = extensions.createInstrumentation(
            sdkKey = "mobile-key",
            options = options,
            logger = ObserveLogger.build(options.logAdapter, options.loggerName, options.debug),
        )

        assertInstanceOf(DefaultInstrumentation::class.java, instrumentation)
        assertNotNull(extensions.sessionReplayInstaller)
    }
}
