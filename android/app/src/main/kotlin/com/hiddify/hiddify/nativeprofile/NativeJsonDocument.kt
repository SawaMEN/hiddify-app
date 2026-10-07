package com.hiddify.hiddify.nativeprofile

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.Strictness
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import java.io.StringReader

/** JSON-pointer based edits retain numeric precision, nulls, array order and escaped keys. */
object NativeJsonDocument {
    private val pretty = GsonBuilder().setPrettyPrinting().serializeNulls().disableHtmlEscaping().create()

    fun parse(text: String): JsonElement {
        require(text.toByteArray(Charsets.UTF_8).size <= NativeProfileTransfer.MAX_CONFIG_BYTES) { "Configuration exceeds 8 MiB" }
        // Bound nesting before handing user input to a recursive parser.
        var depth = 0
        var quoted = false
        var escaped = false
        for (char in text) {
            if (quoted) {
                if (escaped) escaped = false
                else if (char == '\\') escaped = true
                else if (char == '"') quoted = false
            } else when (char) {
                '"' -> quoted = true
                '{', '[' -> { depth++; require(depth <= 128) { "JSON nesting exceeds 128 levels" } }
                '}', ']' -> depth--
            }
        }
        require(text.isNotBlank()) { "Configuration is empty" }
        JsonReader(StringReader(text)).use { reader ->
            reader.strictness = Strictness.STRICT
            val result = JsonParser.parseReader(reader)
            require(reader.peek() == JsonToken.END_DOCUMENT) { "Unexpected content after JSON" }
            return result
        }
    }

    fun format(value: JsonElement): String = pretty.toJson(value)
    fun child(path: String, key: String): String = "$path/${key.replace("~", "~0").replace("/", "~1")}"
    fun parent(path: String): String = path.substringBeforeLast('/', "")
    fun key(path: String): String = path.substringAfterLast('/').replace("~1", "/").replace("~0", "~")

    fun at(root: JsonElement, path: String): JsonElement {
        require(path.isEmpty() || path.startsWith('/')) { "Invalid JSON pointer" }
        var current = root
        if (path.isEmpty()) return current
        for (part in path.substring(1).split('/')) {
            val key = part.replace("~1", "/").replace("~0", "~")
            current = when {
                current.isJsonObject -> current.asJsonObject.get(key) ?: error("JSON key no longer exists")
                current.isJsonArray -> current.asJsonArray[index(key, current.asJsonArray.size())]
                else -> error("JSON value has no children")
            }
        }
        return current
    }

    fun replace(root: JsonElement, path: String, value: JsonElement): JsonElement {
        if (path.isEmpty()) return value.deepCopy()
        val copy = root.deepCopy()
        val owner = at(copy, parent(path))
        if (owner.isJsonObject) {
            require(owner.asJsonObject.has(key(path))) { "JSON key no longer exists" }
            owner.asJsonObject.add(key(path), value.deepCopy())
        } else owner.asJsonArray.set(index(key(path), owner.asJsonArray.size()), value.deepCopy())
        return copy
    }

    fun remove(root: JsonElement, path: String): JsonElement {
        require(path.isNotEmpty()) { "Cannot delete the root" }
        val copy = root.deepCopy()
        val owner = at(copy, parent(path))
        if (owner.isJsonObject) {
            require(owner.asJsonObject.has(key(path))) { "JSON key no longer exists" }
            owner.asJsonObject.remove(key(path))
        } else owner.asJsonArray.remove(index(key(path), owner.asJsonArray.size()))
        return copy
    }

    fun rename(root: JsonElement, path: String, newKey: String): JsonElement {
        require(path.isNotEmpty()) { "Cannot rename the root" }
        val owner = at(root, parent(path)).asJsonObject
        val oldKey = key(path)
        require(owner.has(oldKey)) { "JSON key no longer exists" }
        require(newKey == oldKey || !owner.has(newKey)) { "JSON key already exists" }
        val rebuilt = JsonObject()
        owner.entrySet().forEach { (key, value) -> rebuilt.add(if (key == oldKey) newKey else key, value.deepCopy()) }
        return replace(root, parent(path), rebuilt)
    }

    fun add(root: JsonElement, path: String, name: String, value: JsonElement): JsonElement {
        val copy = root.deepCopy()
        val owner = at(copy, path)
        when {
            owner.isJsonObject -> {
                require(!owner.asJsonObject.has(name)) { "JSON key already exists" }
                owner.asJsonObject.add(name, value.deepCopy())
            }
            owner.isJsonArray -> owner.asJsonArray.add(value.deepCopy())
            else -> error("JSON value has no children")
        }
        return copy
    }

    fun merge(root: JsonElement, path: String, values: JsonObject): JsonElement {
        val copy = root.deepCopy()
        val owner = at(copy, path).asJsonObject
        values.entrySet().forEach { (key, value) -> owner.add(key, value.deepCopy()) }
        return copy
    }

    fun empty(type: String): JsonElement = when (type) {
        "object" -> JsonObject()
        "array" -> JsonArray()
        "string" -> com.google.gson.JsonPrimitive("")
        "boolean" -> com.google.gson.JsonPrimitive(false)
        "number" -> com.google.gson.JsonPrimitive(0)
        "null" -> JsonNull.INSTANCE
        else -> error("Unknown JSON type")
    }

    fun search(root: JsonElement, query: String): List<String> {
        if (query.isBlank()) return emptyList()
        val result = mutableListOf<String>()
        fun visit(value: JsonElement, path: String) {
            val children = when {
                value.isJsonObject -> value.asJsonObject.entrySet().map { it.key to it.value }
                value.isJsonArray -> value.asJsonArray.mapIndexed { index, child -> index.toString() to child }
                else -> emptyList()
            }
            for ((key, child) in children) {
                val next = NativeJsonDocument.child(path, key)
                val text = if (child.isJsonPrimitive) child.asJsonPrimitive.asString else if (child.isJsonNull) "null" else ""
                if (key.contains(query, ignoreCase = true) || text.contains(query, ignoreCase = true)) result += next
                visit(child, next)
            }
        }
        visit(root, "")
        return result
    }

    private fun index(key: String, size: Int): Int {
        val index = key.toIntOrNull() ?: error("Invalid array index")
        require(index in 0 until size) { "JSON array index no longer exists" }
        return index
    }
}
