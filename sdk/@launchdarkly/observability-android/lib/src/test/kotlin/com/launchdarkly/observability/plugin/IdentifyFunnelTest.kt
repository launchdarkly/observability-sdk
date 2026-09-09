package com.launchdarkly.observability.plugin

import com.launchdarkly.observability.sdk.LDObserve
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Covers the single identify funnel: both the `afterIdentify` hook (`LDClient.identify`) and the
 * manual [com.launchdarkly.observability.sdk.LDObserve.identify] API must reach the emitter that
 * caches context keys and broadcasts to Session Replay.
 */
class IdentifyFunnelTest {

    private lateinit var emitter: TrackEmitting
    private lateinit var exporter: ObservabilityHookExporter

    @BeforeEach
    fun setup() {
        emitter = mockk(relaxed = true)
        exporter = ObservabilityHookExporter(
            withSpans = false,
            withValue = false,
            tracerProvider = { null }
        ).apply { trackEmitter = emitter }
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `hook identify reaches the emitter`() {
        exporter.afterIdentify(mapOf("user" to "user-key"), "user-key", completed = true)

        verify(exactly = 1) {
            emitter.recordIdentify(mapOf("user" to "user-key"), "user-key", Attributes.empty())
        }
    }

    @Test
    fun `incomplete identify is dropped`() {
        exporter.afterIdentify(mapOf("user" to "user-key"), "user-key", completed = false)

        verify(exactly = 0) { emitter.recordIdentify(any(), any(), any()) }
    }

    @Test
    fun `manual identify carries its attributes to the emitter`() {
        val attributes = Attributes.of(AttributeKey.stringKey("plan"), "pro")

        exporter.sendAfterIdentify(mapOf("org" to "org-key"), "org:org-key", attributes)

        verify(exactly = 1) {
            emitter.recordIdentify(mapOf("org" to "org-key"), "org:org-key", attributes)
        }
    }

    @Test
    fun `identity attributes cannot clobber the reserved identify fields`() {
        mockkObject(LDObserve)
        val logged = slot<Attributes>()
        every { LDObserve.recordLog(any(), any(), capture(logged), any()) } answers { }

        exporter.sendAfterIdentify(
            mapOf("user" to "user-key"),
            "user-key",
            Attributes.of(
                AttributeKey.stringKey("key"), "spoofed",
                AttributeKey.stringKey("user"), "spoofed"
            )
        )

        assertEquals("user-key", logged.captured.get(AttributeKey.stringKey("key")))
        assertEquals("user-key", logged.captured.get(AttributeKey.stringKey("user")))
    }
}
