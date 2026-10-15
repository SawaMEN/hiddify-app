package com.hiddify.hiddify.nativelog

import org.junit.Assert.*
import org.junit.Test

class NativeRecentLogLinesTest {
    @Test fun preservesLastLinesAndSourcePresenceAcrossEviction() {
        val lines = NativeRecentLogLines(3)
        assertFalse(lines.append("empty.log", "\n \r\n\t"))
        assertTrue(lines.append("first.log", "one\r\n\n two \nthree\r"))
        val snapshot = lines.snapshot()
        assertEquals(listOf("[first.log] one", "[first.log]  two ", "[first.log] three"), snapshot)
        assertTrue(lines.append("second.log", "four\nfive\nsix\nseven"))
        assertEquals(listOf("[second.log] five", "[second.log] six", "[second.log] seven"), lines.snapshot())
        assertEquals(3, snapshot.size) // Published snapshots cannot change underneath Compose.
    }

    @Test fun largeSourcesMatchPreviousTakeLastBehavior() {
        val sources = (0..8).map { index -> "log$index" to (0..20_000).joinToString("\n") { if (it % 3 == 0) " " else "line$it" } }
        val expected = sources.flatMap { (name, text) -> text.lineSequence().filter { it.isNotBlank() }.map { "[$name] $it" }.toList() }.takeLast(500)
        val lines = NativeRecentLogLines(500)
        sources.forEach { (name, text) -> assertTrue(lines.append(name, text)) }
        assertEquals(expected, lines.snapshot())
        val cachedLines = NativeRecentLogLines(500)
        sources.forEach { (name, text) ->
            val source = NativeRecentLogLines(500).apply { append(name, text) }.snapshot()
            cachedLines.appendPresented(source)
        }
        assertEquals(expected, cachedLines.snapshot())
    }
}
