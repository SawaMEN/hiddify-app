package com.hiddify.hiddify.nativelog

/** Preserve the log message while removing terminal formatting from the native text view. */
object NativeLogPresentation {
    private val ansi = Regex("\u001B\\[[0-?]*[ -/]*[@-~]")
    private val severity = Regex("\\b(TRACE|DEBUG|INFO|WARN|WARNING|ERROR|FATAL|PANIC)\\b", RegexOption.IGNORE_CASE)
    val levels = listOf("TRACE", "DEBUG", "INFO", "WARN", "ERROR", "FATAL", "PANIC")
    fun clean(line: String) = line.replace(ansi, "").replace("\r", "")
    fun level(line: String): String? = severity.find(clean(line))?.value?.uppercase()?.let {
        if (it == "WARNING") "WARN" else it
    }
    fun visible(lines: List<String>, query: String, level: String?): List<String> =
        lines.asSequence().map(::clean).filter {
            it.contains(query.trim(), ignoreCase = true) && (level == null || NativeLogPresentation.level(it) == level)
        }.toList()
}
