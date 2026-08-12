package com.launchdarkly.observability.otlp.json

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Helpers mirroring the Swift `JsonTestHelpers` for poking at the OTLP/JSON tree produced by the
 * per-signal adapters. All parsing is done through kotlinx-serialization (no reflection), so the
 * resulting tree faithfully represents what the HTTP client will actually send on the wire.
 */
object JsonTestHelpers {
    private val ENCODER: Json = Json { encodeDefaults = false; explicitNulls = false }

    fun <T> encodeToTree(value: T, serializer: KSerializer<T>): JsonObject {
        val text = ENCODER.encodeToString(serializer, value)
        return ENCODER.parseToJsonElement(text).jsonObject
    }

    fun obj(element: JsonElement?): JsonObject {
        checkNotNull(element) { "expected object, got null" }
        check(element is JsonObject) { "expected object, got $element" }
        return element
    }

    fun array(element: JsonElement?): JsonArray {
        checkNotNull(element) { "expected array, got null" }
        check(element is JsonArray) { "expected array, got $element" }
        return element
    }

    fun stringOrNull(element: JsonElement?): String? {
        if (element == null || element is JsonNull) return null
        val primitive = element as? JsonPrimitive ?: error("expected primitive, got $element")
        if (!primitive.isString) error("expected string, got $primitive")
        return primitive.content
    }

    fun string(element: JsonElement?): String =
        stringOrNull(element) ?: error("expected string, got null")

    fun intOrNull(element: JsonElement?): Int? {
        if (element == null || element is JsonNull) return null
        val primitive = element as? JsonPrimitive ?: error("expected primitive, got $element")
        return primitive.content.toIntOrNull()
            ?: error("expected int, got ${primitive.content}")
    }

    fun int(element: JsonElement?): Int =
        intOrNull(element) ?: error("expected int, got null")

    fun doubleOrNull(element: JsonElement?): Double? {
        if (element == null || element is JsonNull) return null
        val primitive = element as? JsonPrimitive ?: error("expected primitive, got $element")
        return primitive.double
    }

    fun double(element: JsonElement?): Double =
        doubleOrNull(element) ?: error("expected double, got null")

    fun boolean(element: JsonElement?): Boolean {
        val primitive = element as? JsonPrimitive ?: error("expected primitive, got $element")
        return primitive.boolean
    }
}
