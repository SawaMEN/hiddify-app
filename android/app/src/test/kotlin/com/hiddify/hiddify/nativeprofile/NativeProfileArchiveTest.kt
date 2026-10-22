package com.hiddify.hiddify.nativeprofile

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.*
import org.junit.Test

class NativeProfileArchiveTest {
    private fun archive(vararg entries: Pair<String, ByteArray>): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            for ((name, content) in entries) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(content)
                zip.closeEntry()
            }
        }
        return output.toByteArray()
    }
    private fun text(value: String) = value.toByteArray(Charsets.UTF_8)
    private fun read(bytes: ByteArray) = NativeProfileArchive.read(ByteArrayInputStream(bytes))

    @Test fun plainDocumentsRetainTheExistingImportPath() {
        val result = read(text("{\"outbounds\": []}"))
        assertFalse(result.archive)
        assertEquals("", result.profiles.single().name)
        assertEquals("{\"outbounds\": []}", result.profiles.single().content)
    }

    @Test fun zipDetectionWorksWithShortReadsFromDocumentProvider() {
        val bytes = archive("settings.json" to text("{\"proxy\":true}"))
        val fragmented = object : ByteArrayInputStream(bytes) {
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
                super.read(buffer, offset, length.coerceAtMost(1))
        }
        val imported = NativeProfileArchive.read(fragmented)
        assertTrue(imported.archive)
        assertEquals("settings", imported.profiles.single().name)
    }

    @Test fun importsEachSupportedFormatAsAnIndependentProfile() {
        val result = read(archive(
            "folder/config.json" to text("{\"outbounds\": []}"),
            "servers.yaml" to text("proxies:\n  - name: Example"),
            "links.txt" to text("vless://example"),
            "readme.md" to text("Ignore this file"),
        ))
        assertTrue(result.archive)
        assertEquals(listOf("config", "servers", "links"), result.profiles.map { it.name })
        assertEquals(3, result.profiles.size)
        assertEquals("proxies:\n  - name: Example", result.profiles[1].content)
    }

    @Test fun duplicateNamesAreDistinctAndNoFilesAreExtracted() {
        val result = read(archive(
            "group-a/config.json" to text("{\"id\":1}"),
            "group-b/config.json" to text("{\"id\":2}"),
        ))
        assertEquals(listOf("config", "config (2)"), result.profiles.map { it.name })
    }

    @Test fun rejectsTraversalEvenForIgnoredFiles() {
        for (unsafe in listOf("../evil.json", "/absolute/file.txt", "folder/../bad.yaml", "C:/drive/file.json")) {
            val error = runCatching { read(archive(unsafe to text("payload"))) }.exceptionOrNull()
            assertNotNull("Must reject: $unsafe", error)
        }
    }

    @Test fun refusesTooManyFilesAndProfiles() {
        val many = Array(17) { i -> "config-$i.json" to text("{\"id\":$i}") }
        assertTrue(runCatching { read(archive(*many)) }.isFailure)
        val entries = Array(65) { i -> "metadata-$i.md" to text("readme") }
        assertTrue(runCatching { read(archive(*entries)) }.isFailure)
    }

    @Test fun refusesZipBombExpandedOverEightMibPerEntry() {
        val huge = ByteArray(NativeProfileTransfer.MAX_CONFIG_BYTES + 1) { 'A'.code.toByte() }
        assertTrue(runCatching { read(archive("huge.txt" to huge)) }.isFailure)
    }

    @Test fun rejectsInvalidUtf8AndUnsupportedOnlyArchives() {
        assertTrue(runCatching { read(archive("bad.yaml" to byteArrayOf(0xc3.toByte(), 0x28))) }.isFailure)
        assertTrue(runCatching { read(archive("photo.png" to text("not a profile"))) }.isFailure)
    }

    @Test fun rejectsBlankProfilesRatherThanImportingInvalidEmptyContent() {
        assertTrue(runCatching { read(archive("empty.json" to text("   "))) }.isFailure)
    }
}
