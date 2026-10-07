package com.hiddify.hiddify.nativerouting

import com.google.gson.Gson
import com.google.gson.JsonParser
import java.io.ByteArrayInputStream
import org.junit.Assert.*
import org.junit.Test

class NativePerAppBackupTest {
    private val fixture = """{"include":{"selected":["com.example.one","android"],"deselected":["com.example.two"]},"exclude":{"selected":["com.example.three"],"deselected":[]}}"""

    @Test fun roundTripsBothModesInFlutterFormat() {
        val backup = NativePerAppBackupCodec.decode(fixture)
        assertEquals(setOf("com.example.one", "android"), backup.include.selected)
        assertEquals(setOf("com.example.two"), backup.include.deselected)
        assertEquals(backup, NativePerAppBackupCodec.decode(NativePerAppBackupCodec.encode(backup)))
        assertEquals(setOf("include", "exclude"), JsonParser.parseString(NativePerAppBackupCodec.encode(backup)).asJsonObject.keySet())
    }

    @Test fun acceptsLegacyDoubleEncodedJson() {
        assertEquals(NativePerAppBackupCodec.decode(fixture), NativePerAppBackupCodec.decode(Gson().toJson(fixture)))
    }

    @Test fun retainsChoicesForAppsThatAreNotInstalled() {
        assertEquals(setOf("com.example.future"), NativePerAppBackupCodec.decode("""{"include":{"selected":["com.example.future"]}}""").include.selected)
    }

    @Test fun allowsOneModeAndDefaultsTheOther() {
        assertEquals(NativePerAppModeBackup(), NativePerAppBackupCodec.decode("""{"include":{}}""").exclude)
    }

    @Test fun rejectsConflictingChoicesBeforeRestoring() {
        assertThrows(IllegalArgumentException::class.java) {
            NativePerAppBackupCodec.decode("""{"include":{"selected":["com.example.one"],"deselected":["com.example.one"]}}""")
        }
    }

    @Test fun rejectsWrongDocumentsAndTypes() {
        for (input in listOf("{}", "[]", "null", """{"include":null}""", """{"include":{"selected":true}}""", """{"include":{"selected":[12]}}""")) {
            assertThrows(input, IllegalArgumentException::class.java) { NativePerAppBackupCodec.decode(input) }
        }
    }

    @Test fun validatesPackageNames() {
        for (pkg in listOf("", "../data/profile", "com.example; DROP TABLE", "bad package", "a".repeat(256))) {
            assertThrows(IllegalArgumentException::class.java) {
                NativePerAppBackupCodec.encode(NativePerAppBackup(include = NativePerAppModeBackup(selected = setOf(pkg))))
            }
        }
    }

    @Test fun deduplicatesAndSortsExportedChoices() {
        val input = """{"exclude":{"selected":["com.example.z","com.example.a","com.example.z"]}}"""
        val backup = NativePerAppBackupCodec.decode(input)
        assertEquals(2, backup.exclude.selected.size)
        val output = NativePerAppBackupCodec.encode(backup)
        assertTrue(output.indexOf("com.example.a") < output.indexOf("com.example.z"))
    }

    @Test fun boundsFileReadsAndHandlesBom() {
        assertEquals(fixture, NativePerAppBackupCodec.read(ByteArrayInputStream(("\uFEFF" + fixture).toByteArray())))
        assertThrows(IllegalArgumentException::class.java) {
            NativePerAppBackupCodec.read(ByteArrayInputStream(ByteArray(NativePerAppBackupCodec.MAX_BYTES + 1)))
        }
    }

    @Test fun boundsEntryCount() {
        assertThrows(IllegalArgumentException::class.java) {
            NativePerAppBackupCodec.encode(NativePerAppBackup(include = NativePerAppModeBackup(
                selected = (0..10_000).map { "com.example.app$it" }.toSet(),
            )))
        }
    }
}
