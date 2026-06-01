package com.launchdarkly.observability.plugin

import com.launchdarkly.observability.context.ObserveLogger
import com.launchdarkly.sdk.LDValue
import io.mockk.mockk
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Exercises the `launchdarkly.track` span emission path on [ObservabilityHookExporter].
 *
 * Verifies behavior for both call sites that funnel through this method:
 *  - direct [com.launchdarkly.observability.sdk.LDObserve.track] calls
 *  - the LD client's `afterTrack` hook (see [ObservabilityHook.afterTrack])
 *
 * Uses an [InMemorySpanExporter] + [SdkTracerProvider] passed via the exporter's
 * `tracerProvider` lambda so each test can assert on the emitted spans without spinning up
 * the full [com.launchdarkly.observability.client.ObservabilityService].
 *
 * NOTE: the `productAnalyticsApi.trackEvents` gate lives on
 * [com.launchdarkly.observability.client.ObservabilityService.track] (the caller), not the
 * exporter — by design, so the exporter stays decoupled from the options struct. Exercising
 * that gate requires the full service; it is structurally a one-line early return and is
 * covered by integration testing.
 */
class ObservabilityHookExporterTrackTest {

    private lateinit var spanExporter: InMemorySpanExporter
    private lateinit var tracerProvider: SdkTracerProvider
    private lateinit var testTracer: Tracer
    private lateinit var exporter: ObservabilityHookExporter

    @BeforeEach
    fun setUp() {
        spanExporter = InMemorySpanExporter.create()
        tracerProvider = SdkTracerProvider.builder()
            .addSpanProcessor(SimpleSpanProcessor.create(spanExporter))
            .build()
        testTracer = tracerProvider.get("test")
        exporter = ObservabilityHookExporter(
            withSpans = true,
            withValue = true,
            tracerProvider = { testTracer },
            logger = mockk(relaxed = true),
        )
    }

    @AfterEach
    fun tearDown() {
        tracerProvider.shutdown()
    }

    @Nested
    @DisplayName("Span name and basic shape")
    inner class SpanShape {

        @Test
        fun `track emits a launchdarkly_track span with the key attribute`() {
            exporter.track(
                key = "purchase_completed",
                data = null,
                metricValue = null,
            )

            val spans = spanExporter.finishedSpanItems
            assertEquals(1, spans.size)
            val span = spans.single()
            assertEquals(ObservabilityHookExporter.LD_TRACK_SPAN_NAME, span.name)
            assertEquals("purchase_completed", span.attributes.get(AttributeKey.stringKey("key")))
        }

        @Test
        fun `metricValue is written as the value attribute when present`() {
            exporter.track(
                key = "checkout",
                data = null,
                metricValue = 42.5,
            )

            val span = spanExporter.finishedSpanItems.single()
            assertEquals(42.5, span.attributes.get(AttributeKey.doubleKey("value")))
        }

        @Test
        fun `value attribute is omitted when metricValue is null`() {
            exporter.track(
                key = "checkout",
                data = null,
                metricValue = null,
            )

            val span = spanExporter.finishedSpanItems.single()
            // The attribute key should not be present at all when metricValue was null —
            // this prevents spurious `value = 0.0` rows downstream.
            assertNull(span.attributes.get(AttributeKey.doubleKey("value")))
        }
    }

    @Nested
    @DisplayName("LDValue data spread")
    inner class DataSpread {

        @Test
        fun `object data spreads each property as a top-level attribute`() {
            val data = LDValue.buildObject()
                .put("product", "Hat")
                .put("price", 19.99)
                .put("inStock", true)
                .build()

            exporter.track(
                key = "purchase",
                data = data,
                metricValue = null,
            )

            val attrs = spanExporter.finishedSpanItems.single().attributes
            assertEquals("Hat", attrs.get(AttributeKey.stringKey("product")))
            assertEquals(19.99, attrs.get(AttributeKey.doubleKey("price")))
            assertEquals(true, attrs.get(AttributeKey.booleanKey("inStock")))
        }

        @Test
        fun `null data does not throw and produces no spread attributes`() {
            exporter.track(
                key = "click",
                data = null,
                metricValue = null,
            )

            val span = spanExporter.finishedSpanItems.single()
            // Only the `key` attribute should be present — nothing leaked from a null payload.
            assertEquals("click", span.attributes.get(AttributeKey.stringKey("key")))
        }

        @Test
        fun `LDValue ofNull does not throw and produces no spread attributes`() {
            exporter.track(
                key = "click",
                data = LDValue.ofNull(),
                metricValue = null,
            )

            val span = spanExporter.finishedSpanItems.single()
            assertNotNull(span)
            assertEquals("click", span.attributes.get(AttributeKey.stringKey("key")))
        }

        @Test
        fun `non-object LDValue payloads are skipped without throwing`() {
            // Strings, numbers, booleans, and arrays are valid LDValue payloads on `track(...)`
            // but cannot be spread as an object map; matches the JS reference's
            // `typeof === 'object'` guard.
            exporter.track(
                key = "click",
                data = LDValue.of("a string payload"),
                metricValue = null,
            )

            val span = spanExporter.finishedSpanItems.single()
            // Nothing was spread from the string payload.
            assertNull(span.attributes.get(AttributeKey.stringKey("a string payload")))
        }
    }

    @Nested
    @DisplayName("Context-key and SDK metadata spread")
    inner class ContextAndMeta {

        @Test
        fun `afterIdentify caches context keys so subsequent track spans carry them`() {
            exporter.afterIdentify(
                contextKeys = mapOf("user" to "alice", "org" to "team-a"),
                canonicalKey = "user:alice:org:team-a",
                completed = true,
            )

            exporter.track(key = "click", data = null, metricValue = null)

            val attrs = spanExporter.finishedSpanItems.single().attributes
            // Bare top-level keys, NOT namespaced (matches JS reference).
            assertEquals("alice", attrs.get(AttributeKey.stringKey("user")))
            assertEquals("team-a", attrs.get(AttributeKey.stringKey("org")))
        }

        @Test
        fun `non-completed afterIdentify does not overwrite the cached context keys`() {
            exporter.afterIdentify(
                contextKeys = mapOf("user" to "alice"),
                canonicalKey = "user:alice",
                completed = true,
            )
            // Subsequent identify failed — we should still have alice cached.
            exporter.afterIdentify(
                contextKeys = mapOf("user" to "bob"),
                canonicalKey = "user:bob",
                completed = false,
            )

            exporter.track(key = "click", data = null, metricValue = null)

            val attrs = spanExporter.finishedSpanItems.single().attributes
            assertEquals("alice", attrs.get(AttributeKey.stringKey("user")))
        }

        @Test
        fun `setMetaAttributes adds SDK metadata to subsequent track spans`() {
            val meta = Attributes.builder()
                .put(ObservabilityHookExporter.ATTR_TELEMETRY_SDK_NAME, "android-client-sdk")
                .put(ObservabilityHookExporter.ATTR_TELEMETRY_SDK_VERSION, "5.12.0")
                .put(ObservabilityHookExporter.ATTR_FEATURE_FLAG_SET_ID, "mob-test-key")
                .put(ObservabilityHookExporter.ATTR_FEATURE_FLAG_PROVIDER_NAME, "LaunchDarkly")
                .put(ObservabilityHookExporter.ATTR_LD_APPLICATION_ID, "com.example.app")
                .put(ObservabilityHookExporter.ATTR_LD_APPLICATION_VERSION, "1.2.3")
                .build()
            exporter.setMetaAttributes(meta)

            exporter.track(key = "click", data = null, metricValue = null)

            val attrs = spanExporter.finishedSpanItems.single().attributes
            assertEquals("android-client-sdk", attrs.get(AttributeKey.stringKey(ObservabilityHookExporter.ATTR_TELEMETRY_SDK_NAME)))
            assertEquals("5.12.0", attrs.get(AttributeKey.stringKey(ObservabilityHookExporter.ATTR_TELEMETRY_SDK_VERSION)))
            assertEquals("mob-test-key", attrs.get(AttributeKey.stringKey(ObservabilityHookExporter.ATTR_FEATURE_FLAG_SET_ID)))
            assertEquals("LaunchDarkly", attrs.get(AttributeKey.stringKey(ObservabilityHookExporter.ATTR_FEATURE_FLAG_PROVIDER_NAME)))
            assertEquals("com.example.app", attrs.get(AttributeKey.stringKey(ObservabilityHookExporter.ATTR_LD_APPLICATION_ID)))
            assertEquals("1.2.3", attrs.get(AttributeKey.stringKey(ObservabilityHookExporter.ATTR_LD_APPLICATION_VERSION)))
        }
    }

    @Nested
    @DisplayName("Exception safety")
    inner class ExceptionSafety {

        @Test
        fun `track swallows exceptions from a failing tracer and returns Unit`() {
            val throwingExporter = ObservabilityHookExporter(
                withSpans = true,
                withValue = true,
                tracerProvider = { throw RuntimeException("boom") },
                logger = mockk(relaxed = true),
            )

            // Must not throw — hook safety contract.
            throwingExporter.track(
                key = "click",
                data = null,
                metricValue = null,
            )

            // And nothing landed in the exporter, which is also fine.
            assertTrue(spanExporter.finishedSpanItems.isEmpty())
        }
    }
}
