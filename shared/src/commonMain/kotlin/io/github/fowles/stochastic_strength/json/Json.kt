package io.github.fowles.stochastic_strength.json

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * A deliberately small facade with org.json's shape over kotlinx.serialization's JSON tree.
 *
 * `org.json` lives in `android.jar`, not the common stdlib. Rather than rewrite `BackupJson`
 * against a different API, this implements exactly the subset it uses, so that file changes
 * by three import lines. Number output differs from org.json in one visible way: org.json
 * prints 100.0 as `100`; this prints `100.0`. Both parsers read both.
 */
class JSONException(message: String, cause: Throwable? = null) : Exception(message, cause)

private val compactJson = Json
private val prettyJson = Json { prettyPrint = true; prettyPrintIndent = "  " }

private fun toElement(value: Any): JsonElement = when (value) {
    is JsonElement -> value
    is JSONObject -> value.toJsonElement()
    is JSONArray -> value.toJsonElement()
    is String -> JsonPrimitive(value)
    is Boolean -> JsonPrimitive(value)
    is Number -> JsonPrimitive(value)
    else -> JsonPrimitive(value.toString())
}

private fun JsonElement.primitiveOrThrow(where: String): JsonPrimitive =
    this as? JsonPrimitive ?: throw JSONException("$where is not a primitive")

private fun JsonPrimitive.longOrThrow(where: String): Long =
    if (this is JsonNull) throw JSONException("$where is null")
    else content.toLongOrNull() ?: content.toDoubleOrNull()?.toLong() ?: throw JSONException("$where is not a number: $content")

private fun JsonPrimitive.doubleOrThrow(where: String): Double =
    if (this is JsonNull) throw JSONException("$where is null")
    else content.toDoubleOrNull() ?: throw JSONException("$where is not a number: $content")

private fun JsonPrimitive.booleanOrThrow(where: String): Boolean =
    if (this is JsonNull) throw JSONException("$where is null")
    else content.toBooleanStrictOrNull() ?: throw JSONException("$where is not a boolean: $content")

private fun JsonPrimitive.stringOrThrow(where: String): String =
    if (this is JsonNull) throw JSONException("$where is null") else content

class JSONObject {
    private val map = LinkedHashMap<String, JsonElement>()

    constructor()

    constructor(source: String) {
        val element = try {
            compactJson.parseToJsonElement(source)
        } catch (e: Exception) {
            throw JSONException("Malformed JSON: ${e.message}", e)
        }
        val obj = element as? JsonObject ?: throw JSONException("Top-level value is not an object")
        map.putAll(obj)
    }

    internal constructor(obj: JsonObject) { map.putAll(obj) }

    /** Like org.json: `null` removes the key; use [NULL] to store an explicit JSON null. */
    fun put(key: String, value: Any?): JSONObject {
        if (value == null) map.remove(key) else map[key] = toElement(value)
        return this
    }

    fun has(key: String): Boolean = map.containsKey(key)
    fun isNull(key: String): Boolean = map[key].let { it == null || it is JsonNull }

    private fun element(key: String): JsonElement = map[key] ?: throw JSONException("No value for $key")

    fun getLong(key: String): Long = element(key).primitiveOrThrow(key).longOrThrow(key)
    fun getInt(key: String): Int = getLong(key).toInt()
    fun getDouble(key: String): Double = element(key).primitiveOrThrow(key).doubleOrThrow(key)
    fun getBoolean(key: String): Boolean = element(key).primitiveOrThrow(key).booleanOrThrow(key)
    fun getString(key: String): String = element(key).primitiveOrThrow(key).stringOrThrow(key)
    fun getJSONObject(key: String): JSONObject =
        JSONObject(element(key) as? JsonObject ?: throw JSONException("$key is not an object"))
    fun getJSONArray(key: String): JSONArray =
        JSONArray(element(key) as? JsonArray ?: throw JSONException("$key is not an array"))

    fun optString(key: String, default: String = ""): String =
        (map[key] as? JsonPrimitive)?.takeUnless { it is JsonNull }?.content ?: default
    fun optInt(key: String, default: Int): Int =
        (map[key] as? JsonPrimitive)?.takeUnless { it is JsonNull }?.content
            ?.let { it.toLongOrNull() ?: it.toDoubleOrNull()?.toLong() }?.toInt() ?: default
    fun optBoolean(key: String, default: Boolean): Boolean =
        (map[key] as? JsonPrimitive)?.takeUnless { it is JsonNull }?.content?.toBooleanStrictOrNull() ?: default
    fun optJSONArray(key: String): JSONArray? = (map[key] as? JsonArray)?.let { JSONArray(it) }

    fun toJsonElement(): JsonObject = JsonObject(map)
    override fun toString(): String = compactJson.encodeToString(JsonElement.serializer(), toJsonElement())
    fun toString(indentFactor: Int): String =
        if (indentFactor > 0) prettyJson.encodeToString(JsonElement.serializer(), toJsonElement()) else toString()

    companion object {
        /** Sentinel for an explicit JSON null, as in org.json. */
        val NULL: Any = JsonNull
    }
}

class JSONArray {
    private val list = ArrayList<JsonElement>()

    constructor()
    internal constructor(arr: JsonArray) { list.addAll(arr) }

    fun put(value: Any?): JSONArray {
        list.add(if (value == null) JsonNull else toElement(value))
        return this
    }

    fun length(): Int = list.size

    private fun element(index: Int): JsonElement =
        list.getOrNull(index) ?: throw JSONException("Index $index out of range [0, ${list.size})")

    fun getJSONObject(index: Int): JSONObject =
        JSONObject(element(index) as? JsonObject ?: throw JSONException("[$index] is not an object"))
    fun getString(index: Int): String = element(index).primitiveOrThrow("[$index]").stringOrThrow("[$index]")

    fun toJsonElement(): JsonArray = JsonArray(list)
    override fun toString(): String = compactJson.encodeToString(JsonElement.serializer(), toJsonElement())
}
