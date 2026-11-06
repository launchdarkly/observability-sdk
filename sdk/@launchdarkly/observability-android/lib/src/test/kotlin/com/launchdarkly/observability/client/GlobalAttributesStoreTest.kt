package com.launchdarkly.observability.client

import com.launchdarkly.observability.context.ObserveLogger
import com.launchdarkly.observability.sdk.LDObserve
import io.mockk.mockk
import io.mockk.verify
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class GlobalAttributesStoreTest {

    private val logger = mockk<ObserveLogger>(relaxed = true)

    @BeforeEach
    fun setup() {
        GlobalAttributesStore.clear()
        GlobalAttributesStore.logger = logger
    }

    @AfterEach
    fun tearDown() {
        GlobalAttributesStore.clear()
        GlobalAttributesStore.logger = null
    }

    @Test
    fun `setAttributes merges by key`() {
        GlobalAttributesStore.set(Attributes.of(TENANT, "acme", REGION, "us"))
        GlobalAttributesStore.set(Attributes.of(TENANT, "globex", PLAN, "pro"))

        val current = GlobalAttributesStore.current()
        assertEquals(3, current.size())
        assertEquals("globex", current.get(TENANT))
        assertEquals("us", current.get(REGION))
        assertEquals("pro", current.get(PLAN))
    }

    @Test
    fun `setAttributes replaces a key re-set with a different value type`() {
        GlobalAttributesStore.set(Attributes.of(AttributeKey.stringKey("build"), "42"))
        GlobalAttributesStore.set(Attributes.of(AttributeKey.longKey("build"), 42L))

        val current = GlobalAttributesStore.current()
        assertEquals(1, current.size())
        assertEquals(42L, current.get(AttributeKey.longKey("build")))
    }

    @Test
    fun `removeAttributes removes only the given keys`() {
        GlobalAttributesStore.set(Attributes.of(TENANT, "acme", REGION, "us", PLAN, "pro"))

        GlobalAttributesStore.remove(listOf("tenant", "plan", "unknown"))

        val current = GlobalAttributesStore.current()
        assertEquals(1, current.size())
        assertEquals("us", current.get(REGION))
    }

    @Test
    fun `reserved session keys are ignored and logged`() {
        GlobalAttributesStore.set(
            Attributes.builder()
                .put("session.id", "spoofed")
                .put("highlight.session_id", "spoofed")
                .put("tenant", "acme")
                .build()
        )

        val current = GlobalAttributesStore.current()
        assertEquals(1, current.size())
        assertEquals("acme", current.get(TENANT))
        assertNull(current.get(AttributeKey.stringKey("session.id")))
        assertNull(current.get(AttributeKey.stringKey("highlight.session_id")))
        verify(exactly = 2) { logger.warn(any<String>()) }
    }

    @Test
    fun `merge lets explicit attributes win over globals`() {
        GlobalAttributesStore.set(Attributes.of(TENANT, "acme", REGION, "us"))

        val merged = GlobalAttributesStore.merge(
            Attributes.of(TENANT, "per-call", AttributeKey.longKey("region"), 7L)
        )

        assertEquals(2, merged.size())
        assertEquals("per-call", merged.get(TENANT))
        assertEquals(7L, merged.get(AttributeKey.longKey("region")))
    }

    @Test
    fun `LDObserve setAttributes before initialization updates the global store`() {
        LDObserve.setAttributes(Attributes.of(TENANT, "acme"))
        LDObserve.setAttributes(mapOf("plan" to "pro", "seats" to 5))

        val current = GlobalAttributesStore.current()
        assertEquals("acme", current.get(TENANT))
        assertEquals("pro", current.get(PLAN))
        assertEquals(5L, current.get(AttributeKey.longKey("seats")))

        LDObserve.removeAttributes(listOf("tenant"))
        assertNull(GlobalAttributesStore.current().get(TENANT))
    }

    @Test
    fun `concurrent writers do not lose updates`() {
        val threads = (0 until 8).map { t ->
            Thread {
                repeat(50) { i -> GlobalAttributesStore.set(Attributes.of(AttributeKey.longKey("k$t.$i"), i.toLong())) }
            }
        }
        threads.forEach { it.start() }
        threads.forEach { it.join() }

        assertEquals(400, GlobalAttributesStore.current().size())
        assertTrue(GlobalAttributesStore.current().get(AttributeKey.longKey("k7.49")) == 49L)
    }

    private companion object {
        val TENANT: AttributeKey<String> = AttributeKey.stringKey("tenant")
        val REGION: AttributeKey<String> = AttributeKey.stringKey("region")
        val PLAN: AttributeKey<String> = AttributeKey.stringKey("plan")
    }
}
