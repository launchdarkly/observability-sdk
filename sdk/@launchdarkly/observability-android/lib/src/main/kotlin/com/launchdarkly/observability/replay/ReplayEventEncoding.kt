package com.launchdarkly.observability.replay

import com.launchdarkly.observability.json.JsonByteWriter
import com.launchdarkly.observability.json.JsonWritable

/*
 * rrweb JSON encoding of the replay models. Optional fields are left out when null rather than written
 * as `null`, and fields are written in declaration order.
 */

internal fun ReplayEventsInput.asJsonWritable(): JsonWritable = JsonWritable { writer ->
    writer.beginObject()
    writer.name("events").beginArray()
    for (event in events) writer.write(event)
    writer.endArray()
    writer.endObject()
}

internal fun JsonByteWriter.write(event: Event) {
    beginObject()
    name("type").value(event.type.value)
    name("data")
    when (val data = event.data) {
        is EventDataUnion.StandardEventData -> write(data.data)
        is EventDataUnion.CustomEventDataWrapper -> anyValue(data.data)
    }
    name("timestamp").value(event.timestamp)
    name("_sid").value(event.sid)
    endObject()
}

private fun JsonByteWriter.write(data: EventData) {
    beginObject()
    data.source?.let { name("source").value(it.value) }
    data.type?.let { name("type").value(it.value) }
    data.texts?.let { texts ->
        name("texts").beginArray()
        for (text in texts) value(text)
        endArray()
    }
    data.attributes?.let { attributes ->
        name("attributes").beginArray()
        for (attribute in attributes) write(attribute)
        endArray()
    }
    optional("href", data.href)
    optional("width", data.width)
    optional("height", data.height)
    data.node?.let {
        name("node")
        write(it)
    }
    data.removes?.let { removes ->
        name("removes").beginArray()
        for (removal in removes) {
            beginObject()
            name("parentId").value(removal.parentId)
            name("id").value(removal.id)
            endObject()
        }
        endArray()
    }
    data.adds?.let { adds ->
        name("adds").beginArray()
        for (addition in adds) {
            beginObject()
            name("parentId").value(addition.parentId)
            optional("nextId", addition.nextId)
            name("node")
            write(addition.node)
            endObject()
        }
        endArray()
    }
    optional("id", data.id)
    optional("x", data.x)
    optional("y", data.y)
    endObject()
}

private fun JsonByteWriter.write(node: EventNode) {
    beginObject()
    name("type").value(node.type.value)
    optional("name", node.name)
    optional("tagName", node.tagName)
    node.attributes?.let { stringMap("attributes", it) }
    name("childNodes").beginArray()
    for (child in node.childNodes) write(child)
    endArray()
    optional("rootId", node.rootId)
    optional("id", node.id)
    endObject()
}

private fun JsonByteWriter.write(attributes: Attributes) {
    beginObject()
    optional("id", attributes.id)
    attributes.attributes?.let { stringMap("attributes", it) }
    endObject()
}

private fun JsonByteWriter.stringMap(name: String, map: Map<String, String>) {
    name(name).beginObject()
    for ((key, value) in map) name(key).value(value)
    endObject()
}
