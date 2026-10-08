package com.hiddify.hiddify.nativelog

import android.content.Context
import com.hiddify.hiddify.Settings
import java.io.File
import java.io.IOException

data class NativeLogSnapshot(
    val lines: List<String>,
    val sources: List<String>,
)

class NativeLogRepository(private val context: Context) {
    companion object {
        private const val MAX_SOURCE_BYTES = 256 * 1024L
        private const val MAX_LINES = 500
    }

    private val tailCache = NativeLogTailCache()

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

    fun readRecent(): NativeLogSnapshot {
        val lines = ArrayList<String>()
        val sources = ArrayList<String>()
        for (file in candidateFiles()) {
            if (!file.isFile || file.length() <= 0L) continue
            val text = try { tailCache.read(file, MAX_SOURCE_BYTES) }
            catch (_: IOException) { continue }
            catch (_: SecurityException) { continue }
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
        return NativeLogSnapshot(
            lines = lines.takeLast(MAX_LINES),
            sources = sources,
        )
    }

    /** Copy the selected original log for a FileProvider grant; never share private source paths. */
    fun export(app: Boolean): File {
        val working = workingDir()
        val candidates = if (app) listOf(File(working, "app.log"))
            else listOf(File(working, "box.log"), File(working, "data/box.log"))
        val source = candidates.firstOrNull { it.isFile && it.length() > 0 }
        val directory = File(context.cacheDir, "logs").apply { mkdirs() }
        if (source == null) {
            throw java.io.FileNotFoundException("No log file available")
        }
        val expiry = System.currentTimeMillis() - 24 * 60 * 60 * 1000L
        directory.listFiles()?.filter { it.isFile && it.lastModified() < expiry }?.forEach { it.delete() }
        // A later share must not overwrite a file still being read by another app.
        val destination = File.createTempFile(if (app) "app-" else "box-", ".log", directory)
        try {
            source.copyTo(destination, overwrite = true)
            return destination
        } catch (error: Exception) {
            destination.delete()
            throw error
        }
    }

    fun clear() { tailCache.clear(); NativeLogFiles.clear(candidateFiles()) }

}
