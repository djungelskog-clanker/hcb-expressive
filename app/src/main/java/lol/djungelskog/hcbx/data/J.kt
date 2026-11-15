package lol.djungelskog.hcbx.data

import kotlinx.serialization.json.*

/** Null-safe wrapper around a JSON element. The HCB API is loosely typed, so we read it tolerantly. */
@JvmInline
value class J(val e: JsonElement?) {
    operator fun get(key: String): J = J((e as? JsonObject)?.get(key))
    operator fun get(i: Int): J = J((e as? JsonArray)?.getOrNull(i))

    val s: String? get() = (e as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content
    val str: String get() = s ?: ""
    val long: Long? get() = s?.toDoubleOrNull()?.toLong()
    val l: Long get() = long ?: 0L
    val int: Int get() = (long ?: 0L).toInt()
    val bool: Boolean get() = s == "true"
    val list: List<J> get() = (e as? JsonArray)?.map { J(it) } ?: emptyList()
    val keys: Set<String> get() = (e as? JsonObject)?.keys ?: emptySet()
    val exists: Boolean get() = e != null && e !is JsonNull
    val isArray: Boolean get() = e is JsonArray

    /** Treats the value as a list: either a bare array or a paginated `{ data: [...] }` envelope. */
    val items: List<J> get() = if (e is JsonArray) list else this["data"].list
    val hasMore: Boolean get() = this["has_more"].bool
    val id: String get() = this["id"].str

    override fun toString() = e?.toString() ?: "null"

    companion object {
        val json = Json { ignoreUnknownKeys = true; isLenient = true }
        fun parse(text: String): J = runCatching { J(json.parseToJsonElement(text)) }.getOrDefault(J(null))
    }
}

fun toJson(v: Any?): JsonElement = when (v) {
    null -> JsonNull
    is JsonElement -> v
    is Boolean -> JsonPrimitive(v)
    is Number -> JsonPrimitive(v)
    is Map<*, *> -> JsonObject(v.entries.filter { it.value != null }.associate { it.key.toString() to toJson(it.value) })
    is Iterable<*> -> JsonArray(v.map { toJson(it) })
    else -> JsonPrimitive(v.toString())
}
