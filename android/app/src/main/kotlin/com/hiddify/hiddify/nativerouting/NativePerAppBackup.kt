package com.hiddify.hiddify.nativerouting

import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import java.io.ByteArrayOutputStream
import java.io.InputStream

data class NativePerAppModeBackup(
    val selected: Set<String> = emptySet(),
    val deselected: Set<String> = emptySet(),
)

data class NativePerAppBackup(
    val include: NativePerAppModeBackup = NativePerAppModeBackup(),
    val exclude: NativePerAppModeBackup = NativePerAppModeBackup(),
)

object NativePerAppBackupCodec {
    const val MAX_BYTES = 1024 * 1024
    private const val MAX_ENTRIES = 10_000
    private val packagePattern = Regex("(?:[A-Za-z][A-Za-z0-9_]*\\.)+[A-Za-z][A-Za-z0-9_]*|android")
    private val gson = GsonBuilder().setPrettyPrinting().create()

    fun read(input: InputStream): String {
        val bytes = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = input.read(buffer)
            if (count == -1) break
            require(bytes.size() + count <= MAX_BYTES) { "Application backup exceeds 1 MiB" }
            bytes.write(buffer, 0, count)
        }
        return bytes.toString(Charsets.UTF_8.name()).removePrefix("\uFEFF")
    }

    fun decode(text: String): NativePerAppBackup {
        require(text.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "Application backup exceeds 1 MiB" }
        var root = JsonParser.parseString(text.removePrefix("\uFEFF"))
        // Earlier Flutter versions exported a JSON string containing the JSON document.
        if (root.isJsonPrimitive && root.asJsonPrimitive.isString) root = JsonParser.parseString(root.asString)
        require(root.isJsonObject) { "Application backup must be a JSON object" }
        require(root.asJsonObject.has("include") || root.asJsonObject.has("exclude")) {
            "Application backup must contain include or exclude lists"
        }
        val backup = NativePerAppBackup(
            include = mode(root.asJsonObject.get("include")),
            exclude = mode(root.asJsonObject.get("exclude")),
        )
        validate(backup)
        return backup
    }

    fun encode(backup: NativePerAppBackup): String {
        validate(backup)
        val normalized = NativePerAppBackup(
            include = NativePerAppModeBackup(backup.include.selected.toSortedSet(), backup.include.deselected.toSortedSet()),
            exclude = NativePerAppModeBackup(backup.exclude.selected.toSortedSet(), backup.exclude.deselected.toSortedSet()),
        )
        return gson.toJson(normalized).also {
            require(it.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "Application backup exceeds 1 MiB" }
        }
    }

    fun validate(backup: NativePerAppBackup) {
        val modes = listOf(backup.include, backup.exclude)
        require(modes.sumOf { it.selected.size + it.deselected.size } <= MAX_ENTRIES) { "Too many applications in backup" }
        for (mode in modes) {
            require(mode.selected.intersect(mode.deselected).isEmpty()) { "Conflicting application selection in backup" }
            for (pkg in mode.selected + mode.deselected) {
                require(pkg.length <= 255 && packagePattern.matches(pkg)) { "Invalid application package in backup" }
            }
        }
    }

    private fun mode(element: JsonElement?): NativePerAppModeBackup {
        if (element == null) return NativePerAppModeBackup()
        require(element.isJsonObject) { "Application routing mode must be an object" }
        return NativePerAppModeBackup(packages(element.asJsonObject.get("selected")), packages(element.asJsonObject.get("deselected")))
    }

    private fun packages(element: JsonElement?): Set<String> {
        if (element == null) return emptySet()
        require(element.isJsonArray) { "Application package list must be an array" }
        require(element.asJsonArray.size() <= MAX_ENTRIES) { "Too many applications in backup" }
        return element.asJsonArray.mapTo(linkedSetOf()) {
            require(it.isJsonPrimitive && it.asJsonPrimitive.isString) { "Application package must be a string" }
            it.asString
        }
    }
}

/** Same bits as Drift; native checkboxes represent the effective two-state selection. */
object NativePerAppFlags {
    const val USER_SELECTION = 1
    const val FORCE_DESELECTION = 2
    const val AUTO_SELECTION = 4
    const val MANUAL_MASK = USER_SELECTION or FORCE_DESELECTION

    fun selected(flags: Int): Boolean =
        flags and FORCE_DESELECTION == 0 && flags and (USER_SELECTION or AUTO_SELECTION) != 0

    fun toggle(flags: Int): Int = if (selected(flags)) {
        if (flags and AUTO_SELECTION != 0) (flags and USER_SELECTION.inv()) or FORCE_DESELECTION
        else flags and MANUAL_MASK.inv()
    } else {
        (flags and FORCE_DESELECTION.inv()) or USER_SELECTION
    }

    fun restoreManual(existing: Int, selected: Boolean): Int =
        (existing and MANUAL_MASK.inv()) or if (selected) USER_SELECTION else FORCE_DESELECTION
}
