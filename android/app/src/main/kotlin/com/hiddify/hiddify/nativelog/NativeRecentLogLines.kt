package com.hiddify.hiddify.nativelog

/** Retain only the display window while consuming already byte-bounded file tails. */
internal class NativeRecentLogLines(private val limit: Int) {
    private val lines = java.util.ArrayDeque<String>()
    init { require(limit > 0) }

    /** Returns whether this source contains any displayable lines, even if later evicted. */
    fun append(sourceName: String, text: String): Boolean {
        var hasLines = false
        for (line in text.lineSequence()) {
            if (line.isBlank()) continue
            hasLines = true
            if (lines.size == limit) lines.removeFirst()
            lines.addLast("[$sourceName] $line")
        }
        return hasLines
    }

    fun snapshot(): List<String> = lines.toList()
}
