package com.launchdarkly.observability.client

import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * Unit tests for the shared `click` attribute builder used by both the automatic tap
 * instrumentation and the manual `trackClick` API (taxonomy §4.1).
 */
class ClickAttributesTest {
    private val eventType = AttributeKey.stringKey("event.type")
    private val eventTag = AttributeKey.stringKey("event.tag")
    private val eventClassname = AttributeKey.stringKey("event.classname")
    private val eventId = AttributeKey.stringKey("event.id")
    private val eventText = AttributeKey.stringKey("event.text")
    private val eventScreenId = AttributeKey.stringKey("event.screen_id")
    private val eventX = AttributeKey.longKey("event.x")
    private val eventY = AttributeKey.longKey("event.y")

    @Test
    fun `auto tap shape includes tag, classname, id, text, screen_id and coordinates`() {
        val attrs = ClickAttributes.build(
            tag = "Button",
            classname = "android.widget.Button",
            id = "save_profile_btn",
            text = "Save",
            screenId = "com.example.app.ProfileActivity",
            x = 586L,
            y = 33L,
        )

        assertEquals("click", attrs.get(eventType))
        assertEquals("Button", attrs.get(eventTag))
        assertEquals("android.widget.Button", attrs.get(eventClassname))
        assertEquals("save_profile_btn", attrs.get(eventId))
        assertEquals("Save", attrs.get(eventText))
        assertEquals("com.example.app.ProfileActivity", attrs.get(eventScreenId))
        assertEquals(586L, attrs.get(eventX))
        assertEquals(33L, attrs.get(eventY))
    }

    @Test
    fun `optional fields are omitted when null`() {
        val attrs = ClickAttributes.build(
            tag = null,
            classname = null,
            id = null,
            text = null,
            screenId = null,
            x = null,
            y = null,
        )

        // event.type is always present; everything else is omitted.
        assertEquals("click", attrs.get(eventType))
        assertNull(attrs.get(eventTag))
        assertNull(attrs.get(eventClassname))
        assertNull(attrs.get(eventId))
        assertNull(attrs.get(eventText))
        assertNull(attrs.get(eventScreenId))
        assertNull(attrs.get(eventX))
        assertNull(attrs.get(eventY))
    }

    @Test
    fun `reserved event fields win over caller properties`() {
        val properties = Attributes.builder()
            .put(eventId, "from_properties")
            .put(eventType, "not_click")
            .put(AttributeKey.stringKey("custom"), "kept")
            .build()

        val attrs = ClickAttributes.build(
            tag = null,
            classname = null,
            id = "reserved_id",
            text = null,
            screenId = null,
            x = null,
            y = null,
            properties = properties,
        )

        // Reserved fields take precedence; unrelated custom properties are preserved.
        assertEquals("reserved_id", attrs.get(eventId))
        assertEquals("click", attrs.get(eventType))
        assertEquals("kept", attrs.get(AttributeKey.stringKey("custom")))
    }

    @Test
    fun `context keys win over caller properties but not reserved fields`() {
        val properties = Attributes.builder()
            .put(AttributeKey.stringKey("accountId"), "from_properties")
            .build()
        val contextKeys = Attributes.builder()
            .put(AttributeKey.stringKey("accountId"), "from_context")
            .build()

        val attrs = ClickAttributes.build(
            tag = null,
            classname = null,
            id = null,
            text = null,
            screenId = null,
            x = null,
            y = null,
            contextKeyAttributes = contextKeys,
            properties = properties,
        )

        assertEquals("from_context", attrs.get(AttributeKey.stringKey("accountId")))
    }
}
