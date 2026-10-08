package com.hiddify.hiddify.nativerouting

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.hiddify.hiddify.Settings
import com.hiddify.hiddify.constant.PerAppProxyMode
import java.io.File

data class NativePerAppSnapshot(
    val mode: String,
    val apps: List<NativeInstalledApp>,
    val selectedPackages: Set<String>,
    val flags: Map<String, Int> = emptyMap(),
    val loaded: Boolean = false,
)

/**
 * Native implementation of the Drift app_proxy_entries table used by the Flutter UI.
 *
 * Flags are kept byte-for-byte compatible:
 *  bit 0 = user selection
 *  bit 1 = forced deselection
 *  bit 2 = automatic selection
 */
class NativePerAppRepository(private val context: Context) {

    private val databaseFile: File
        get() {
            val flutterDatabase = File(context.getDir("flutter", Context.MODE_PRIVATE), "db.sqlite")
            val earlyNativeDatabase = File(context.filesDir, "db.sqlite")
            return when {
                flutterDatabase.exists() -> flutterDatabase
                earlyNativeDatabase.exists() -> earlyNativeDatabase
                else -> flutterDatabase
            }
        }

    fun snapshot(): NativePerAppSnapshot {
        val mode =
            Settings.perAppProxyMode.takeIf {
                it == PerAppProxyMode.OFF ||
                    it == PerAppProxyMode.INCLUDE ||
                    it == PerAppProxyMode.EXCLUDE
            } ?: PerAppProxyMode.OFF

        val apps = installedApps()
        val installed = apps.asSequence().map { it.packageName }.toSet()
        val flags = if (mode == PerAppProxyMode.OFF) emptyMap() else openDatabase().use { db ->
            db.rawQuery("SELECT pkg_name, flags FROM app_proxy_entries WHERE mode = ?", arrayOf(mode)).use { cursor ->
                buildMap { while (cursor.moveToNext()) { val pkg = cursor.getString(0)
                    if (pkg in installed) put(pkg, cursor.getInt(1))
                } }
            }
        }
        val selected = flags.filterValues(NativePerAppFlags::selected).keys
        if (mode != PerAppProxyMode.OFF) Settings.setPerAppProxyPackages(mode, selected)
        return NativePerAppSnapshot(mode, apps, selected, flags, loaded = true)
    }

    fun setMode(mode: String): NativePerAppSnapshot {
        require(mode in setOf(PerAppProxyMode.OFF, PerAppProxyMode.INCLUDE, PerAppProxyMode.EXCLUDE)) {
            "Unsupported per-app routing mode"
        }
        val preferences = context.getSharedPreferences("FlutterSharedPreferences", Context.MODE_PRIVATE)
        if ((preferences.all["flutter.auto_apps_selection_region"] as? String).orEmpty().isNotEmpty()) {
            val previousMode = Settings.perAppProxyMode
            openDatabase().use { db ->
                db.beginTransaction()
                try {
                    db.execSQL("UPDATE app_proxy_entries SET flags = flags & ? WHERE mode = ?",
                        arrayOf<Any>(NativePerAppFlags.AUTO_SELECTION.inv(), previousMode))
                    db.delete("app_proxy_entries", "mode = ? AND flags = 0", arrayOf(previousMode))
                    db.setTransactionSuccessful()
                } finally { db.endTransaction() }
            }
            preferences.edit().remove("flutter.auto_apps_selection_region").remove("flutter.auto_apps_selection_last_update").apply()
        }
        Settings.perAppProxyMode = mode
        if (mode != PerAppProxyMode.OFF) {
            Settings.setPerAppProxyPackages(mode, activePackages(mode))
        }
        return snapshot()
    }

    fun togglePackage(mode: String, packageName: String): NativePerAppSnapshot {
        require(mode == PerAppProxyMode.INCLUDE || mode == PerAppProxyMode.EXCLUDE) {
            "Choose an include or exclude mode first"
        }
        require(packageName.isNotBlank() && packageName != context.packageName) {
            "Invalid application package"
        }

        openDatabase().use { db ->
            db.beginTransaction()
            try {
                val existingFlags =
                    db.rawQuery(
                        "SELECT flags FROM app_proxy_entries WHERE mode = ? AND pkg_name = ? LIMIT 1",
                        arrayOf(mode, packageName),
                    ).use { cursor ->
                        if (cursor.moveToFirst()) cursor.getInt(0) else null
                    }

                val newFlags = NativePerAppFlags.toggle(existingFlags ?: 0)
                if (newFlags == 0) {
                    db.delete("app_proxy_entries", "mode = ? AND pkg_name = ?", arrayOf(mode, packageName))
                } else {
                    db.insertWithOnConflict(
                        "app_proxy_entries",
                        null,
                        ContentValues().apply {
                            put("mode", mode)
                            put("pkg_name", packageName)
                            put("flags", newFlags)
                        },
                        SQLiteDatabase.CONFLICT_REPLACE,
                    ).also { check(it != -1L) { "Unable to save application selection" } }
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        }

        val selected = activePackages(mode)
        Settings.perAppProxyMode = mode
        Settings.setPerAppProxyPackages(mode, selected)
        return snapshot()
    }

    fun clear(mode: String): NativePerAppSnapshot {
        if (mode == PerAppProxyMode.INCLUDE || mode == PerAppProxyMode.EXCLUDE) {
            openDatabase().use { db ->
                db.delete("app_proxy_entries", "mode = ?", arrayOf(mode))
            }
            Settings.setPerAppProxyPackages(mode, emptyList())
        }
        context.getSharedPreferences("FlutterSharedPreferences", Context.MODE_PRIVATE).edit()
            .remove("flutter.auto_apps_selection_region").remove("flutter.auto_apps_selection_last_update").apply()
        return snapshot()
    }

    fun exportBackup(): String = openDatabase().use { db ->
        fun modeBackup(mode: String): NativePerAppModeBackup {
            val selected = linkedSetOf<String>()
            val deselected = linkedSetOf<String>()
            db.rawQuery("SELECT pkg_name, flags FROM app_proxy_entries WHERE mode = ? ORDER BY pkg_name", arrayOf(mode)).use { cursor ->
                while (cursor.moveToNext()) {
                    val pkg = cursor.getString(0)
                    val flags = cursor.getInt(1)
                    if (flags and NativePerAppFlags.FORCE_DESELECTION != 0) deselected += pkg
                    else if (flags and NativePerAppFlags.USER_SELECTION != 0) selected += pkg
                }
            }
            return NativePerAppModeBackup(selected, deselected)
        }
        NativePerAppBackupCodec.encode(NativePerAppBackup(
            include = modeBackup(PerAppProxyMode.INCLUDE),
            exclude = modeBackup(PerAppProxyMode.EXCLUDE),
        ))
    }

    fun importBackup(backup: NativePerAppBackup): NativePerAppSnapshot {
        NativePerAppBackupCodec.validate(backup)
        openDatabase().use { db ->
            db.beginTransaction()
            try {
                // Replace manual choices in both modes while retaining automatic policy bits.
                db.execSQL("UPDATE app_proxy_entries SET flags = flags & ? WHERE mode IN (?, ?)",
                    arrayOf<Any>(NativePerAppFlags.MANUAL_MASK.inv(), PerAppProxyMode.INCLUDE, PerAppProxyMode.EXCLUDE))
                db.execSQL("DELETE FROM app_proxy_entries WHERE flags = 0 AND mode IN (?, ?)",
                    arrayOf(PerAppProxyMode.INCLUDE, PerAppProxyMode.EXCLUDE))
                fun apply(mode: String, packages: Set<String>, selected: Boolean) {
                    for (pkg in packages) {
                        val existing = db.rawQuery("SELECT flags FROM app_proxy_entries WHERE mode = ? AND pkg_name = ?", arrayOf(mode, pkg)).use { cursor ->
                            if (cursor.moveToFirst()) cursor.getInt(0) else 0
                        }
                        db.insertWithOnConflict("app_proxy_entries", null, ContentValues().apply {
                            put("mode", mode)
                            put("pkg_name", pkg)
                            put("flags", NativePerAppFlags.restoreManual(existing, selected))
                        }, SQLiteDatabase.CONFLICT_REPLACE).also { check(it != -1L) { "Unable to restore application selection" } }
                    }
                }
                apply(PerAppProxyMode.INCLUDE, backup.include.selected, true)
                apply(PerAppProxyMode.INCLUDE, backup.include.deselected, false)
                apply(PerAppProxyMode.EXCLUDE, backup.exclude.selected, true)
                apply(PerAppProxyMode.EXCLUDE, backup.exclude.deselected, false)
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        }
        // Keep the current routing mode; retain uninstalled package choices in the database for
        // later reinstalls, but publish only installed applications to the VPN service.
        return snapshot()
    }

    private fun activePackages(mode: String): Set<String> =
        openDatabase().use { db ->
            db.rawQuery(
                """
                SELECT pkg_name, flags
                FROM app_proxy_entries
                WHERE mode = ?
                ORDER BY pkg_name COLLATE NOCASE ASC
                """.trimIndent(),
                arrayOf(mode),
            ).use { cursor ->
                buildSet {
                    while (cursor.moveToNext()) {
                        val flags = cursor.getInt(1)
                        if (NativePerAppFlags.selected(flags)) {
                            add(cursor.getString(0))
                        }
                    }
                }
            }
        }

    private fun installedApps(): List<NativeInstalledApp> = NativeInstalledApps(context).load()

    private fun openDatabase(): SQLiteDatabase {
        databaseFile.parentFile?.mkdirs()
        val db = SQLiteDatabase.openOrCreateDatabase(databaseFile, null)
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS app_proxy_entries (
                mode TEXT NOT NULL,
                pkg_name TEXT NOT NULL,
                flags INTEGER NOT NULL DEFAULT 0,
                PRIMARY KEY (mode, pkg_name)
            )
            """.trimIndent(),
        )
        return db
    }
}
