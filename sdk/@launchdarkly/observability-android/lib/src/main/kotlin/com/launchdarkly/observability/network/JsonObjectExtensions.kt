package com.launchdarkly.observability.network

import org.json.JSONArray
import org.json.JSONObject

// `opt*` would turn an explicit JSON null into "null" / 0 / false, so absent and null both map to Kotlin null
// here, and a present value of the wrong type throws instead of being coerced to a default.

internal fun JSONObject.stringOrNull(name: String): String? = if (isNull(name)) null else getString(name)

internal fun JSONObject.intOrNull(name: String): Int? = if (isNull(name)) null else getInt(name)

internal fun JSONObject.longOrNull(name: String): Long? = if (isNull(name)) null else getLong(name)

internal fun JSONObject.booleanOrNull(name: String): Boolean? = if (isNull(name)) null else getBoolean(name)

internal fun JSONObject.objectOrNull(name: String): JSONObject? = if (isNull(name)) null else getJSONObject(name)

internal inline fun <T> JSONObject.objectOrNull(name: String, parse: (JSONObject) -> T): T? =
    objectOrNull(name)?.let(parse)

internal inline fun <T> JSONObject.listOrNull(name: String, element: (JSONArray, Int) -> T): List<T>? {
    if (isNull(name)) return null
    val array = getJSONArray(name)
    return List(array.length()) { index -> element(array, index) }
}

internal inline fun <T> JSONObject.objectListOrNull(name: String, parse: (JSONObject) -> T): List<T?>? =
    listOrNull(name) { array, index -> if (array.isNull(index)) null else parse(array.getJSONObject(index)) }
