package com.hiddify.hiddify.nativelog

/** Structured display and minimum-severity filtering from LogsOverviewNotifier. */
object NativeLogPresentation {
    private val ansi = Regex("\u001B\\[[0-?]*[ -/]*[@-~]")
    private val header = Regex("^(?:\\[[^]]+]\\s*)*(?:(?:\\+\\d{4}\\s+)?(\\d{4}-\\d{2}-\\d{2}[ T]\\d{2}:\\d{2}:\\d{2}(?:[.,]\\d+)?(?:Z|[+-]\\d{2}:?\\d{2})?)\\s+)?(TRACE|DEBUG|INFO|WARN|WARNING|ERROR|FATAL|PANIC)\\b\\s*:?\\s*", RegexOption.IGNORE_CASE)
    private val ordered = listOf("TRACE", "DEBUG", "INFO", "WARN", "ERROR", "FATAL", "PANIC")
    val levels = ordered.take(4)
    data class Entry(val message: String, val level: String?, val time: String?)
    fun clean(line: String) = line.replace(ansi, "").replace("\r", "")
    fun entry(line: String): Entry {
        val clean = clean(line)
        val match = header.find(clean) ?: return Entry(clean, null, null)
        val level = match.groupValues[2].uppercase().let { if (it == "WARNING") "WARN" else it }
        return Entry(clean.substring(match.range.last + 1).trim(), level, match.groupValues[1].ifEmpty { null })
    }
    fun level(line: String): String? = entry(line).level
    fun visible(lines: List<String>, query: String, level: String?): List<String> =
        lines.asSequence().map(::clean).filter {
            val entry = entry(it)
            entry.message.contains(query) && (level == null || entry.level == null ||
                ordered.indexOf(entry.level) >= ordered.indexOf(level))
        }.toList()
}
