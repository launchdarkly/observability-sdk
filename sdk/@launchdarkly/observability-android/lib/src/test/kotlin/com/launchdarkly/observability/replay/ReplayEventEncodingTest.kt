package com.launchdarkly.observability.replay

import com.launchdarkly.observability.json.JsonByteWriter
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** Exact rrweb JSON output: field order, left-out optional fields, and the always-present `childNodes`. */
class ReplayEventEncodingTest {

    private fun encode(event: Event): String = JsonByteWriter.encodeToString { write(event) }

    @Test
    fun `an event with empty standard data`() {
        assertEquals(
            """{"type":1,"data":{},"timestamp":0,"_sid":0}""",
            encode(Event(EventType.LOAD, EventDataUnion.StandardEventData(EventData()), 0L, 0))
        )
    }

    @Test
    fun `standard data writes every set field in declaration order, enums by value`() {
        val data = EventData(
            source = IncrementalSource.INPUT,
            type = MouseInteractions.DBL_CLICK,
            texts = listOf("a", ""),
            attributes = listOf(Attributes(), Attributes(id = 1, attributes = linkedMapOf("k" to "v"))),
            href = "h",
            width = 1,
            height = 2,
            node = EventNode(type = NodeType.TEXT, name = "n", rootId = 9, id = 10),
            removes = emptyList(),
            adds = listOf(Addition(parentId = 3, node = EventNode(type = NodeType.ELEMENT, tagName = "img"))),
            id = 3,
            x = 1.5,
            y = -0.0,
        )
        assertEquals(
            """{"type":3,"data":{"source":5,"type":4,"texts":["a",""],"attributes":[{},{"id":1,"attributes":{"k":"v"}}],""" +
                """"href":"h","width":1,"height":2,"node":{"type":3,"name":"n","childNodes":[],"rootId":9,"id":10},"removes":[],""" +
                """"adds":[{"parentId":3,"node":{"type":2,"tagName":"img","childNodes":[]}}],"id":3,"x":1.5,"y":-0.0},""" +
                """"timestamp":9223372036854775807,"_sid":99}""",
            encode(Event(EventType.INCREMENTAL_SNAPSHOT, EventDataUnion.StandardEventData(data), Long.MAX_VALUE, 99))
        )
    }

    @Test
    fun `nodes nest and always carry childNodes`() {
        val node = EventNode(
            type = NodeType.DOCUMENT,
            id = 1,
            childNodes = listOf(
                EventNode(type = NodeType.ELEMENT, tagName = "html", attributes = emptyMap(), id = 2),
            ),
        )
        assertEquals(
            """{"type":2,"data":{"node":{"type":0,"childNodes":[{"type":2,"tagName":"html","attributes":{},"childNodes":[],"id":2}],"id":1}},"timestamp":5,"_sid":1}""",
            encode(Event(EventType.FULL_SNAPSHOT, EventDataUnion.StandardEventData(EventData(node = node)), 5L, 1))
        )
    }

    @Test
    fun `removals and additions`() {
        val data = EventData(
            source = IncrementalSource.MUTATION,
            removes = listOf(Removal(parentId = 1, id = 2)),
            adds = listOf(Addition(parentId = 1, nextId = 4, node = EventNode(type = NodeType.ELEMENT, id = 5))),
        )
        assertEquals(
            """{"type":3,"data":{"source":0,"removes":[{"parentId":1,"id":2}],"adds":[{"parentId":1,"nextId":4,"node":{"type":2,"childNodes":[],"id":5}}]},"timestamp":1,"_sid":1}""",
            encode(Event(EventType.INCREMENTAL_SNAPSHOT, EventDataUnion.StandardEventData(data), 1L, 1))
        )
    }

    @Test
    fun `custom data is written as given, nulls included`() {
        val data = linkedMapOf(
            "tag" to "Viewport",
            "payload" to linkedMapOf("width" to 1, "height" to 2.5, "flag" to true, "none" to null, "list" to listOf(1, "a")),
        )
        assertEquals(
            """{"type":5,"data":{"tag":"Viewport","payload":{"width":1,"height":2.5,"flag":true,"none":null,"list":[1,"a"]}},"timestamp":7,"_sid":3}""",
            encode(Event(EventType.CUSTOM, EventDataUnion.CustomEventDataWrapper(data), 7L, 3))
        )
    }

    @Test
    fun `events input wraps the events in an object`() {
        val input = ReplayEventsInput(
            listOf(
                Event(EventType.LOAD, EventDataUnion.StandardEventData(EventData()), 0L, 0),
                Event(EventType.CUSTOM, EventDataUnion.CustomEventDataWrapper(mapOf("tag" to "Reload")), 1L, 1),
            )
        )
        assertEquals(
            """{"events":[{"type":1,"data":{},"timestamp":0,"_sid":0},{"type":5,"data":{"tag":"Reload"},"timestamp":1,"_sid":1}]}""",
            JsonByteWriter.encodeToString { anyValue(input.asJsonWritable()) }
        )
    }
}
