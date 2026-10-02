package com.launchdarkly.observability.otlp.json

import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

/** A parsed JSON object, with nested objects as [JsonTree] and arrays as [List]. Explicit `null`s stay as `null` values. */
typealias JsonTree = Map<String, Any?>

/**
 * Helpers mirroring the Swift `JsonTestHelpers` for poking at the OTLP/JSON tree produced by the
 * per-signal adapters. The tree is parsed from the exact bytes the HTTP client sends, so it faithfully
 * represents what goes on the wire.
 */
object JsonTestHelpers {

    fun encodeToTree(bytes: ByteArray): JsonTree = obj(parse(String(bytes, Charsets.UTF_8)))

    fun parse(json: String): Any? = toTree(JSONTokener(json).nextValue())

    private fun toTree(value: Any?): Any? = when (value) {
        JSONObject.NULL, null -> null
        is JSONObject -> value.keys().asSequence().associateWith { toTree(value.get(it)) }
        is JSONArray -> List(value.length()) { toTree(value.get(it)) }
        else -> value
    }

    @Suppress("UNCHECKED_CAST")
    fun obj(element: Any?): JsonTree {
        checkNotNull(element) { "expected object, got null" }
        check(element is Map<*, *>) { "expected object, got $element" }
        return element as JsonTree
    }

    fun array(element: Any?): List<Any?> {
        checkNotNull(element) { "expected array, got null" }
        check(element is List<*>) { "expected array, got $element" }
        return element
    }

    fun stringOrNull(element: Any?): String? {
        if (element == null) return null
        check(element is String) { "expected string, got $element" }
        return element
    }

    fun string(element: Any?): String =
        stringOrNull(element) ?: error("expected string, got null")

    fun intOrNull(element: Any?): Int? = when (element) {
        null -> null
        is Number -> element.toInt().also { check(it.toDouble() == element.toDouble()) { "expected int, got $element" } }
        is String -> element.toIntOrNull() ?: error("expected int, got $element")
        else -> error("expected int, got $element")
    }

    fun int(element: Any?): Int =
        intOrNull(element) ?: error("expected int, got null")

    fun doubleOrNull(element: Any?): Double? = when (element) {
        null -> null
        is Number -> element.toDouble()
        is String -> element.toDoubleOrNull() ?: error("expected double, got $element")
        else -> error("expected double, got $element")
    }

    fun double(element: Any?): Double =
        doubleOrNull(element) ?: error("expected double, got null")

    fun boolean(element: Any?): Boolean {
        check(element is Boolean) { "expected boolean, got $element" }
        return element
    }
}
