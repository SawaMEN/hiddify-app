package com.hiddify.hiddify.nativeprofile

import java.io.FilterInputStream
import java.io.InputStream
import java.io.PushbackInputStream
import java.util.zip.ZipInputStream

data class NativeArchiveProfile(val name: String, val content: String)
internal data class NativeProfileDocuments(val archive: Boolean, val profiles: List<NativeArchiveProfile>)

/**
 * Read profile archives entirely in memory; ZIP entry paths are NEVER used as
 * filesystem paths. Independent JSON/YAML files are returned as independent profiles,
 * not concatenated into a syntactically invalid configuration.
 */
internal object NativeProfileArchive {
    private const val MAX_ARCHIVE_BYTES = 16L * 1024L * 1024L
    private const val MAX_EXPANDED_BYTES = 24L * 1024L * 1024L
    private const val MAX_ENTRIES = 64
    private const val MAX_PROFILES = 16
    private val supported = setOf("json", "yaml", "yml", "txt", "conf", "config", "list", "base64")

    fun read(input: InputStream): NativeProfileDocuments {
        val source = PushbackInputStream(input.buffered(), 4)
        val magic = ByteArray(4)
        var count = 0
        // Content providers may return fewer than four bytes per read.
        while (count < magic.size) {
            val received = source.read(magic, count, magic.size - count)
            if (received < 0) break
            if (received == 0) continue
            count += received
        }
        if (count > 0) source.unread(magic, 0, count)
        if (count != 4 || magic[0] != 'P'.code.toByte() || magic[1] != 'K'.code.toByte() ||
            !(magic[2] == 3.toByte() && magic[3] == 4.toByte() ||
                magic[2] == 5.toByte() && magic[3] == 6.toByte())) {
            return NativeProfileDocuments(false, listOf(NativeArchiveProfile("", NativeProfileTransfer.readText(source))))
        }
        return NativeProfileDocuments(true, readZip(source))
    }

    private fun readZip(input: InputStream): List<NativeArchiveProfile> {
        val result = ArrayList<NativeArchiveProfile>()
        val names = HashMap<String, Int>()
        var entries = 0
        var total = 0L
        ZipInputStream(LimitedInputStream(input, MAX_ARCHIVE_BYTES)).use { zip ->
            val buffer = ByteArray(8192)
            while (true) {
                val entry = zip.nextEntry ?: break
                entries++
                require(entries <= MAX_ENTRIES) { "ZIP contains too many entries" }
                val name = entry.name.replace('\\', '/')
                require(isSafeEntryName(name)) { "ZIP has an unsafe entry name" }
                val file = name.substringAfterLast('/')
                val extension = file.substringAfterLast('.', "").lowercase(java.util.Locale.ROOT)
                val candidate = !entry.isDirectory && !file.startsWith('.') && extension in supported
                if (candidate) require(result.size < MAX_PROFILES) { "ZIP contains too many profiles" }
                val bytes = if (candidate) java.io.ByteArrayOutputStream() else null
                var entrySize = 0L
                while (true) {
                    val received = zip.read(buffer)
                    if (received < 0) break
                    entrySize += received
                    total += received
                    require(total <= MAX_EXPANDED_BYTES && entrySize <= NativeProfileTransfer.MAX_CONFIG_BYTES) {
                        "ZIP expanded contents exceed the size limit"
                    }
                    bytes?.write(buffer, 0, received)
                }
                zip.closeEntry()
                if (candidate) {
                    val content = NativeSubscriptionContent.utf8(requireNotNull(bytes).toByteArray())
                    require(content.isNotBlank()) { "ZIP contains an empty profile" }
                    val stem = file.substringBeforeLast('.').take(96).ifBlank { "Profile" }
                    val duplicate = names.getOrDefault(stem, 0) + 1
                    names[stem] = duplicate
                    result += NativeArchiveProfile(if (duplicate == 1) stem else "$stem ($duplicate)", content)
                }
            }
        }
        require(result.isNotEmpty()) { "ZIP does not contain supported profile files" }
        return result
    }

    private fun isSafeEntryName(name: String): Boolean {
        if (name.isBlank() || name.length > 512 || name.startsWith('/') || name.contains(':') ||
            name.any { it.code < 32 }) return false
        val parts = name.split('/')
        return parts.none { it == "." || it == ".." }
    }

    private class LimitedInputStream(input: InputStream, private val limit: Long) : FilterInputStream(input) {
        private var count = 0L
        override fun read(): Int {
            val value = super.read()
            if (value != -1) checkLength(1)
            return value
        }
        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            val amount = super.read(buffer, offset, length)
            if (amount > 0) checkLength(amount.toLong())
            return amount
        }
        private fun checkLength(delta: Long) {
            count += delta
            require(count <= limit) { "ZIP archive exceeds the size limit" }
        }
    }
}
