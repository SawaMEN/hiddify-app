package com.hiddify.hiddify.nativeprofile

import org.junit.Assert.*
import org.junit.Test

class NativeJsonComplexityTest {
    @Test fun tinyValuesAreRejectedBeforeMillionsOfObjectsAreAllocated() {
        val input = "[" + List(100_000) { "0" }.joinToString(",") + "]"
        assertTrue(input.length < NativeProfileTransfer.MAX_CONFIG_BYTES)
        try {
            NativeJsonDocument.parse(input)
            fail("Expected a complexity limit error")
        } catch (error: IllegalArgumentException) {
            assertTrue(error.message.orEmpty().contains("100000"))
        }
    }

    @Test fun countIncludesNestedContainersAndLeaves() {
        val input = "[" + List(50_000) { "[null]" }.joinToString(",") + "]"
        try {
            NativeJsonDocument.parse(input)
            fail("Nested containers must count towards the limit")
        } catch (error: IllegalArgumentException) {
            assertTrue(error.message.orEmpty().contains("100000"))
        }
    }

    @Test fun reasonableLargeDocumentsStillRoundTrip() {
        val input = "[" + List(10_000) { "{\"tag\":\"proxy-$it\",\"port\":443}" }.joinToString(",") + "]"
        val root = NativeJsonDocument.parse(input)
        assertEquals(10_000, root.asJsonArray.size())
        assertEquals(root, NativeJsonDocument.parse(NativeJsonDocument.format(root)))
    }

    @Test fun broadSearchReturnsBoundedResultsInDocumentOrder() {
        val root = NativeJsonDocument.parse("[" + List(5000) { "\"proxy-$it\"" }.joinToString(",") + "]")
        val results = NativeJsonDocument.search(root, "proxy")
        assertEquals(1000, results.size)
        assertEquals("/0", results.first())
        assertEquals("/999", results.last())
        assertEquals(listOf("/4999"), NativeJsonDocument.search(root, "proxy-4999"))
    }
}
