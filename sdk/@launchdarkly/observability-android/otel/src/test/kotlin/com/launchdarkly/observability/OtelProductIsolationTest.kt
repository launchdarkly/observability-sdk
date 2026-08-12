package com.launchdarkly.observability

import com.launchdarkly.observability.api.ObservabilityOptions
import com.launchdarkly.observability.interfaces.Metric
import com.launchdarkly.observability.plugin.Otel
import com.launchdarkly.observability.sdk.LDObserve
import com.launchdarkly.observability.sdk.ObservabilityExtensionsLoader
import io.mockk.mockk
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.logs.Severity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

/**
 * Guards the reason this module exists: `com.launchdarkly:launchdarkly-otel-android` records what
 * the app asks it to and installs nothing else, so it can run alongside another observability SDK.
 *
 * These tests live here rather than in `:lib` on purpose — this module's compile and runtime
 * classpaths are the OTel-only product's, so what is absent here is absent for consumers.
 */
class OtelProductIsolationTest {

    /**
     * OpenTelemetry Android is what discovers and installs instrumentation from the classpath
     * (`AndroidInstrumentationLoader`), and it is the transitive route by which a crash handler
     * would arrive. If any of these resolve, this module has regained a dependency that defeats
     * the point of the artifact.
     */
    @Test
    fun `no OpenTelemetry Android type is on the classpath`() {
        val forbidden = listOf(
            "io.opentelemetry.android.OpenTelemetryRum",
            "io.opentelemetry.android.instrumentation.AndroidInstrumentation",
            "io.opentelemetry.android.instrumentation.AndroidInstrumentationLoader",
            "io.opentelemetry.android.instrumentation.crash.CrashReporterInstrumentation",
            "io.opentelemetry.android.session.SessionManager",
        )

        for (className in forbidden) {
            assertThrows(ClassNotFoundException::class.java, { Class.forName(className) }, className)
        }
    }

    /**
     * The full product's instrumentation must not leak in either. Its absence is what makes
     * [com.launchdarkly.observability.client.ObservabilityInstrumenting] null at runtime, which in
     * turn is what stops the pipeline installing detectors.
     */
    @Test
    fun `no instrumentation from the full artifact is on the classpath`() {
        val forbidden = listOf(
            "com.launchdarkly.observability.client.DefaultInstrumentation",
            "com.launchdarkly.observability.client.UserInteractionManager",
            "com.launchdarkly.observability.client.LaunchTimeInstrumentation",
            "com.launchdarkly.observability.client.AppLaunchTracker",
            "com.launchdarkly.observability.client.screen.ScreenViewManager",
            "com.launchdarkly.observability.plugin.Observability",
            "com.launchdarkly.observability.replay.SessionReplayService",
            "com.launchdarkly.observability.sdk.FullObservabilityExtensions",
        )

        for (className in forbidden) {
            assertThrows(ClassNotFoundException::class.java, { Class.forName(className) }, className)
        }
    }

    /** Without the full artifact there is nothing to install, and asking for it is not an error. */
    @Test
    fun `extensions resolve to nothing when only this artifact is present`() {
        assertEquals(null, ObservabilityExtensionsLoader.load())
    }

    @Test
    fun `the plugin is constructible from this artifact alone`() {
        val plugin = Otel(
            application = mockk(relaxed = true),
            mobileKey = "mobile-key",
            options = ObservabilityOptions(
                serviceName = "public-api-test",
                otlpEndpoint = "http://127.0.0.1:1",
                backendUrl = "http://127.0.0.1:1",
            ),
        )

        assertNotNull(plugin.metadata.name)
        assertEquals(Otel.PLUGIN_NAME, plugin.metadata.name)
    }

    /**
     * The manual recording surface must be reachable without naming any type from the full
     * artifact. Every call is inert (LDObserve delegates to a no-op until a plugin registers) —
     * the value of this test is that it compiles.
     */
    @Test
    fun `the recording API is reachable from this artifact alone`() {
        LDObserve.recordLog("hello", Severity.INFO, Attributes.empty())
        LDObserve.recordError(Error("boom"), Attributes.empty())
        LDObserve.recordMetric(Metric("m", 1.0))
        LDObserve.recordCount(Metric("c", 1.0))
        LDObserve.recordIncr(Metric("i", 1.0))
        LDObserve.recordHistogram(Metric("h", 1.0))
        LDObserve.recordUpDownCounter(Metric("u", 1.0))
        LDObserve.track("purchase", mapOf("sku" to "abc"), 9.99)
        LDObserve.trackScreenView("Home")
        LDObserve.trackClick(id = "cta")
        LDObserve.startSpan("work", Attributes.empty()).end()
        LDObserve.flush()
    }
}
