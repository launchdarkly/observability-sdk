package com.launchdarkly.observability.network

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class ReplaySessionProtocolTest {

    private val json = Json {
        isLenient = true
        ignoreUnknownKeys = true
    }

    @Test
    fun `EventType should serialize as integer`() {
        val eventType = EventType.FULL_SNAPSHOT
        val serialized = json.encodeToJsonElement(EventType.serializer(), eventType)
        
        assertTrue(serialized is JsonPrimitive)
        assertEquals(2, serialized.int)
    }

    @Test
    fun `NodeType should serialize as integer`() {
        val nodeType = NodeType.ELEMENT
        val serialized = json.encodeToJsonElement(NodeType.serializer(), nodeType)
        
        assertTrue(serialized is JsonPrimitive)
        assertEquals(2, serialized.int)
    }

    @Test
    fun `IncrementalSource should serialize as integer`() {
        val source = IncrementalSource.MOUSE_MOVE
        val serialized = json.encodeToJsonElement(IncrementalSource.serializer(), source)
        
        assertTrue(serialized is JsonPrimitive)
        assertEquals(1, serialized.int)
    }

    @Test
    fun `MouseInteractions should serialize as integer`() {
        val interaction = MouseInteractions.CLICK
        val serialized = json.encodeToJsonElement(MouseInteractions.serializer(), interaction)
        
        assertTrue(serialized is JsonPrimitive)
        assertEquals(2, serialized.int)
    }

    @Test
    fun `Event should serialize with integer type field`() {
        val event = Event(
            type = EventType.FULL_SNAPSHOT,
            data = EventDataUnion.StandardEventData(
                EventData(
                    source = IncrementalSource.MOUSE_MOVE,
                    type = MouseInteractions.CLICK
                )
            ),
            timestamp = 1234567890L,
            _sid = 1
        )
        
        val serialized = json.encodeToJsonElement(Event.serializer(), event)
        val typeField = serialized.jsonObject["type"]
        
        assertTrue(typeField is JsonPrimitive)
        assertEquals(2, typeField.int) // FULL_SNAPSHOT = 2
    }

    @Test
    fun `EventDataUnion should serialize with flattened data structure`() {
        val event = Event(
            type = EventType.FULL_SNAPSHOT,
            data = EventDataUnion.StandardEventData(
                EventData(
                    source = IncrementalSource.MOUSE_MOVE,
                    type = MouseInteractions.CLICK,
                    href = "https://example.com",
                    width = 1920,
                    height = 1080
                )
            ),
            timestamp = 1234567890L,
            _sid = 1
        )
        
        val serialized = json.encodeToJsonElement(Event.serializer(), event)
        val dataField = serialized.jsonObject["data"]
        
        // The data field should be a JsonObject containing the EventData fields directly
        assertTrue(dataField is kotlinx.serialization.json.JsonObject)
        val dataObject = dataField as kotlinx.serialization.json.JsonObject
        
        // Verify that the EventData fields are present directly in the data object
        assertTrue(dataObject.containsKey("source"))
        assertTrue(dataObject.containsKey("type"))
        assertTrue(dataObject.containsKey("href"))
        assertTrue(dataObject.containsKey("width"))
        assertTrue(dataObject.containsKey("height"))
        
        // Verify the values
        assertEquals(1, dataObject["source"]?.jsonPrimitive?.int) // MOUSE_MOVE = 1
        assertEquals(2, dataObject["type"]?.jsonPrimitive?.int) // CLICK = 2
        assertEquals("https://example.com", dataObject["href"]?.jsonPrimitive?.content)
        assertEquals(1920, dataObject["width"]?.jsonPrimitive?.int)
        assertEquals(1080, dataObject["height"]?.jsonPrimitive?.int)
    }

    @Test
    fun `EventDataUnion CustomEventData should serialize with flattened data structure`() {
        val event = Event(
            type = EventType.CUSTOM,
            data = EventDataUnion.CustomEventDataWrapper(
                CustomEventData(
                    tag = "Viewport",
                    payload = JsonPrimitive("""{"width":1080,"height":2274}""")
                )
            ),
            timestamp = 1234567890L,
            _sid = 1
        )
        
        val serialized = json.encodeToJsonElement(Event.serializer(), event)
        val dataField = serialized.jsonObject["data"]
        
        // The data field should be a JsonObject containing the CustomEventData fields directly
        assertTrue(dataField is kotlinx.serialization.json.JsonObject)
        val dataObject = dataField as kotlinx.serialization.json.JsonObject
        
        // Verify that the CustomEventData fields are present directly in the data object
        assertTrue(dataObject.containsKey("tag"))
        assertTrue(dataObject.containsKey("payload"))
        
        // Verify the values
        assertEquals("Viewport", dataObject["tag"]?.jsonPrimitive?.content)
        assertEquals("""{"width":1080,"height":2274}""", dataObject["payload"]?.jsonPrimitive?.content)
    }
}
