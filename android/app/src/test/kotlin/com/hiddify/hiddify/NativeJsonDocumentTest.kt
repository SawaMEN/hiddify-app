package com.hiddify.hiddify

import com.hiddify.hiddify.nativeprofile.NativeJsonDocument as Json
import com.google.gson.JsonPrimitive
import org.junit.Assert.*
import org.junit.Test

class NativeJsonDocumentTest {
    @Test fun preservesPrecisionNullsAndEscapedKeysAcrossEdits() {
        val root = Json.parse("""{"a/b~c":{"value":9223372036854775807123,"none":null},"list":[true,0.0000000000000000000123]}""")
        val pointer = Json.child("", "a/b~c")
        assertEquals("/a~1b~0c", pointer)
        val changed = Json.rename(root, Json.child(pointer, "value"), "large")
        assertEquals("9223372036854775807123", Json.at(changed, "$pointer/large").toString())
        assertTrue(Json.at(Json.parse(Json.format(changed)), "$pointer/none").isJsonNull)
        assertEquals("0.0000000000000000000123", Json.at(changed, "/list/1").toString())
        assertTrue(Json.at(root, pointer).asJsonObject.has("value"))
    }

    @Test fun editsNestedArraysWithoutChangingOtherItems() {
        val original = Json.parse("""{"outbounds":[{"tag":"a"},{"tag":"b"}],"keep":42}""")
        val edited = Json.replace(original, "/outbounds/1/tag", JsonPrimitive("new"))
        val added = Json.add(edited, "/outbounds", "", Json.parse("""{"tag":"c"}"""))
        val removed = Json.remove(added, "/outbounds/0")
        assertEquals("new", Json.at(removed, "/outbounds/0/tag").asString)
        assertEquals("c", Json.at(removed, "/outbounds/1/tag").asString)
        assertEquals(42, Json.at(removed, "/keep").asInt)
        assertEquals("a", Json.at(original, "/outbounds/0/tag").asString)
    }

    @Test fun failedEditsAreAtomicAndDoNotOverwriteSiblingKeys() {
        val root = Json.parse("""{"a":1,"b":2}""")
        assertThrows(IllegalArgumentException::class.java) { Json.rename(root, "/a", "b") }
        assertThrows(IllegalArgumentException::class.java) { Json.add(root, "", "a", JsonPrimitive(3)) }
        assertThrows(IllegalArgumentException::class.java) { Json.remove(root, "") }
        assertEquals("{\"a\":1,\"b\":2}", root.toString())
    }

    @Test fun rejectsMalformedAndTrailingJsonWithoutLenientParsing() {
        for (text in listOf("{unquoted:1}", "{\"a\":NaN}", "[1,]", "{}{}", "", "/*comment*/{}")) {
            assertTrue(text, runCatching { Json.parse(text) }.isFailure)
        }
        assertTrue(Json.parse("null").isJsonNull)
    }

    @Test fun findsValuesAndEscapedKeysInDocumentOrder() {
        val root = Json.parse("""{"outbounds":[{"tag":"Tokyo"},{"tag":"Osaka"}],"tokyo/key":null}""")
        assertEquals(listOf("/outbounds/0/tag", "/tokyo~1key"), Json.search(root, "TOKYO"))
        assertEquals(emptyList<String>(), Json.search(root, ""))
    }

    @Test fun templatesMergeOnlyTheirFieldsAndKeepOtherSettings() {
        val root = Json.parse("""{"outbounds":[{"tag":"keep","tls":{"enabled":false},"server":"example.com"}]}""")
        val merged = Json.merge(root, "/outbounds/0", Json.parse("""{"tls":{"enabled":true}}""").asJsonObject)
        assertEquals("keep", Json.at(merged, "/outbounds/0/tag").asString)
        assertEquals("example.com", Json.at(merged, "/outbounds/0/server").asString)
        assertTrue(Json.at(merged, "/outbounds/0/tls/enabled").asBoolean)
        assertFalse(Json.at(root, "/outbounds/0/tls/enabled").asBoolean)
    }

    @Test fun nestingLimitIgnoresBracesInStrings() {
        assertEquals("[[{{", Json.parse("\"[[{{\"").asString)
        assertTrue(runCatching { Json.parse("[".repeat(129) + "0" + "]".repeat(129)) }.isFailure)
    }
    @Test fun rejectsDuplicateKeysAndMalformedPointersWithoutChangingTheDocument() {
        listOf("{\"a\":1,\"a\":2}", "{\"child\":{\"a\":1,\"a\":2}}").forEach {
            assertTrue(runCatching { Json.parse(it) }.isFailure)
        }
        val root = Json.parse("{\"a\":1,\"list\":[10,20]}")
        listOf("a", "/a~", "/a~2").forEach { path ->
            assertTrue(runCatching { Json.replace(root, path, JsonPrimitive(3)) }.isFailure)
            assertTrue(runCatching { Json.remove(root, path) }.isFailure)
            assertTrue(runCatching { Json.rename(root, path, "b") }.isFailure)
        }
        listOf("+1", "01", "-0", " 0").forEach { index ->
            assertTrue(runCatching { Json.at(root, "/list/$index") }.isFailure)
        }
        assertEquals(1, Json.at(root, "/a").asInt)
    }

    @Test fun streamingSearchMatchesReferenceTraversalAndResultLimit() {
        val root = com.google.gson.JsonArray().apply {
            repeat(3000) { index -> add(Json.parse("""{"a/~key":"Hit $index","items":[null,true,$index,{"nested":"miss"}]}""")) }
        }
        fun reference(query: String): List<String> {
            val result = mutableListOf<String>()
            fun visit(value: com.google.gson.JsonElement, path: String) {
                val children = when {
                    value.isJsonObject -> value.asJsonObject.entrySet().map { it.key to it.value }
                    value.isJsonArray -> value.asJsonArray.mapIndexed { index, child -> index.toString() to child }
                    else -> emptyList()
                }
                for ((key, child) in children) {
                    if (result.size >= 1000) return
                    val next = Json.child(path, key)
                    val text = if (child.isJsonPrimitive) child.asString else if (child.isJsonNull) "null" else ""
                    if (key.contains(query, true) || text.contains(query, true)) result += next
                    visit(child, next)
                }
            }
            visit(root, "")
            return result
        }
        for (query in listOf("Hit", "a/~", "null", "true", "12", "nested", "missing", "items")) {
            assertEquals(query, reference(query), Json.search(root, query))
        }
        assertEquals(1000, Json.search(root, "Hit").size)
    }
}
