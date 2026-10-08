package com.hiddify.hiddify.nativelog

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile

/** A writer may truncate or rotate a log while this bounded snapshot is being read. */
internal object NativeLogFiles {
    fun readTail(file: File, maxBytes: Long): String {
        require(maxBytes in 1..Int.MAX_VALUE.toLong())
        RandomAccessFile(file, "r").use { input ->
            val length = input.length()
            val start = (length - maxBytes).coerceAtLeast(0L)
            val beginsOnLine = start == 0L || run {
                input.seek(start - 1)
                input.read() == '\n'.code
            }
            input.seek(start)
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            var remaining = minOf(length - start, maxBytes)
            while (remaining > 0) {
                val count = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                if (count < 0) break
                output.write(buffer, 0, count)
                remaining -= count
            }
            val text = output.toString(Charsets.UTF_8.name())
            return if (beginsOnLine) text else text.substringAfter('\n', "")
        }
    }

    fun clear(files: List<File>) {
        var failure: IOException? = null
        for (file in files) {
            if (!file.exists()) continue
            try { file.writeText("") } catch (error: Exception) {
                val report = IOException("Could not clear ${file.name}", error)
                if (failure == null) failure = report else failure.addSuppressed(report)
            }
        }
        failure?.let { throw it }
    }
}
