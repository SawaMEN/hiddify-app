package com.hiddify.hiddify.nativelog

import android.content.Context
import com.hiddify.hiddify.Settings
import java.io.File
import java.io.RandomAccessFile
import java.time.LocalTime
import java.time.format.DateTimeFormatter

data class NativeLogSnapshot(
    val lines: List<String>,
    val sources: List<String>,
)

class NativeLogRepository(private val context: Context) {
    companion object {
        private const val MAX_SOURCE_BYTES = 256 * 1024L
        private const val MAX_LINES = 500
        private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")
    }

    private fun workingDir(): File {
        val configured = Settings.workingDir
        return if (configured.isNotBlank() && configured != "./") {
            File(configured)
        } else {
            context.getExternalFilesDir(null) ?: context.filesDir
        }
    }

    private fun candidateFiles(): List<File> {
        val working = workingDir()
        return listOf(
            File(working, "data/box.log"),
            File(working, "data/stderr4.log"),
            File(working, "data/stderr3.log"),
            File(working, "box.log"),
            File(working, "stderr.log"),
            File(working, "app.log"),
        ).distinctBy { it.absolutePath }
    }

    fun readRecent(serviceLines: List<String> = emptyList()): NativeLogSnapshot {
        val lines = ArrayList<String>()
        val sources = ArrayList<String>()
        for (file in candidateFiles()) {
            if (!file.isFile || file.length() <= 0L) continue
            val text = readTail(file, MAX_SOURCE_BYTES)
            val fileLines =
                text.lineSequence()
                    .filter { it.isNotBlank() }
                    .map { "[${file.name}] $it" }
                    .toList()
            if (fileLines.isNotEmpty()) {
                sources += file.absolutePath
                lines += fileLines
            }
        }
        if (serviceLines.isNotEmpty()) lines += serviceLines
        return NativeLogSnapshot(
            lines = lines.takeLast(MAX_LINES),
            sources = sources,
        )
    }

    /** Copy the selected original log for a FileProvider grant; never share private source paths. */
    fun export(app: Boolean, serviceLines: List<String>): File {
        val working = workingDir()
        val candidates = if (app) listOf(File(working, "app.log"))
            else listOf(File(working, "box.log"), File(working, "data/box.log"))
        val source = candidates.firstOrNull { it.isFile && it.length() > 0 }
        val directory = File(context.cacheDir, "logs").apply { mkdirs() }
        if (source == null && (!app || serviceLines.isEmpty())) {
            throw java.io.FileNotFoundException("No log file available")
        }
        val expiry = System.currentTimeMillis() - 24 * 60 * 60 * 1000L
        directory.listFiles()?.filter { it.isFile && it.lastModified() < expiry }?.forEach { it.delete() }
        // A later share must not overwrite a file still being read by another app.
        val destination = File.createTempFile(if (app) "app-" else "box-", ".log", directory)
        try {
            if (source != null) source.copyTo(destination, overwrite = true)
            else destination.writeText(serviceLines.joinToString("\n"))
            return destination
        } catch (error: Exception) {
            destination.delete()
            throw error
        }
    }

    fun clear() {
        for (file in candidateFiles()) {
            if (!file.exists()) continue
            runCatching {
                file.parentFile?.mkdirs()
                file.writeText("")
            }
        }
    }

    fun decorateServiceLine(message: String): String {
        val clean = message.trim()
        return "[service ${LocalTime.now().format(timeFormatter)}] $clean"
    }

    private fun readTail(file: File, maxBytes: Long): String {
        RandomAccessFile(file, "r").use { input ->
            val length = input.length()
            val start = (length - maxBytes).coerceAtLeast(0L)
            input.seek(start)
            val bytes = ByteArray((length - start).toInt())
            input.readFully(bytes)
            var text = String(bytes, Charsets.UTF_8)
            if (start > 0L) {
                val newline = text.indexOf('\n')
                if (newline >= 0 && newline + 1 < text.length) {
                    text = text.substring(newline + 1)
                }
            }
            return text
        }
    }
}
