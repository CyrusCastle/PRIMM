package uk.codecymru.primm.controller

object JsonWriter {
    fun write(value: JsonValue): String = StringBuilder().also { append(it, value) }.toString()

    private fun append(out: StringBuilder, value: JsonValue) {
        when (value) {
            is JsonObject -> {
                out.append('{')
                var first = true
                for ((key, item) in value.fields) {
                    if (!first) out.append(',')
                    first = false
                    appendString(out, key)
                    out.append(':')
                    append(out, item)
                }
                out.append('}')
            }
            is JsonArray -> {
                out.append('[')
                value.items.forEachIndexed { i, item ->
                    if (i > 0) out.append(',')
                    append(out, item)
                }
                out.append(']')
            }
            is JsonString -> appendString(out, value.value)
            is JsonNumber -> out.append(number(value.value))
            is JsonBoolean -> out.append(if (value.value) "true" else "false")
            JsonNull -> out.append("null")
        }
    }

    private fun number(value: Double): String {
        if (value.isNaN() || value.isInfinite()) return "null"
        val whole = value.toLong()
        return if (whole.toDouble() == value && kotlin.math.abs(value) < 1e15) whole.toString() else value.toString()
    }

    private fun appendString(out: StringBuilder, text: String) {
        out.append('"')
        for (c in text) {
            when {
                c == '"' -> out.append("\\\"")
                c == '\\' -> out.append("\\\\")
                c == '\n' -> out.append("\\n")
                c == '\r' -> out.append("\\r")
                c == '\t' -> out.append("\\t")
                c < ' ' || c == ' ' || c == ' ' -> {
                    out.append("\\u")
                    out.append(c.code.toString(16).padStart(4, '0'))
                }
                else -> out.append(c)
            }
        }
        out.append('"')
    }
}


internal fun json(vararg fields: Pair<String, JsonValue>): JsonObject = JsonObject(linkedMapOf(*fields))

internal fun jsonOf(text: String): JsonValue = JsonString(text)

internal fun jsonOf(number: Int?): JsonValue = number?.let { JsonNumber(it.toDouble()) } ?: JsonNull

internal fun jsonOf(flag: Boolean?): JsonValue = flag?.let { JsonBoolean(it) } ?: JsonNull

internal fun jsonArray(items: List<JsonValue>): JsonArray = JsonArray(items)

internal fun jsonStringMap(map: Map<String, String>): JsonObject =
    JsonObject(LinkedHashMap<String, JsonValue>().also { m -> map.forEach { (k, v) -> m[k] = JsonString(v) } })


internal fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject

internal fun JsonObject.array(key: String): List<JsonValue> = (this[key] as? JsonArray)?.items ?: emptyList()

internal fun JsonObject.objects(key: String): List<JsonObject> = array(key).filterIsInstance<JsonObject>()

internal fun JsonObject.strings(key: String): List<String> = array(key).mapNotNull { (it as? JsonString)?.value }

internal fun JsonObject.stringMap(key: String): Map<String, String> =
    obj(key)?.fields?.mapNotNull { (k, v) -> (v as? JsonString)?.let { k to it.value } }?.toMap() ?: emptyMap()
