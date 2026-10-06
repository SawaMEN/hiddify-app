package com.hiddify.hiddify.nativerouting

import android.content.ContentValues
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.database.sqlite.SQLiteDatabase
import com.hiddify.hiddify.Settings
import com.hiddify.hiddify.constant.PerAppProxyMode
import java.io.File

data class NativeInstalledApp(
    val packageName: String,
    val label: String,
    val system: Boolean,
)

data class NativePerAppSnapshot(
    val mode: String,
    val apps: List<NativeInstalledApp>,
    val selectedPackages: Set<String>,
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

    companion object {
        private const val USER_SELECTION = 1 shl 0
        private const val FORCE_DESELECTION = 1 shl 1
        private const val AUTO_SELECTION = 1 shl 2
    }

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
        val selected =
            if (mode == PerAppProxyMode.OFF) {
                emptySet()
            } else {
                activePackages(mode).filterTo(linkedSetOf()) { it in installed }
            }
        if (mode != PerAppProxyMode.OFF) Settings.setPerAppProxyPackages(mode, selected)
        return NativePerAppSnapshot(mode = mode, apps = apps, selectedPackages = selected)
    }

    fun setMode(mode: String): NativePerAppSnapshot {
        require(mode in setOf(PerAppProxyMode.OFF, PerAppProxyMode.INCLUDE, PerAppProxyMode.EXCLUDE)) {
            "Unsupported per-app routing mode"
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

                if (existingFlags == null) {
                    db.insertOrThrow(
                        "app_proxy_entries",
                        null,
                        ContentValues().apply {
                            put("mode", mode)
                            put("pkg_name", packageName)
                            put("flags", USER_SELECTION)
                        },
                    )
                } else if ((existingFlags and AUTO_SELECTION) == 0) {
                    db.delete(
                        "app_proxy_entries",
                        "mode = ? AND pkg_name = ?",
                        arrayOf(mode, packageName),
                    )
                } else {
                    val newFlags =
                        when {
                            (existingFlags and FORCE_DESELECTION) != 0 ->
                                existingFlags and FORCE_DESELECTION.inv() and USER_SELECTION.inv()

                            (existingFlags and USER_SELECTION) != 0 ->
                                (existingFlags and USER_SELECTION.inv()) or FORCE_DESELECTION

                            else ->
                                (existingFlags and FORCE_DESELECTION.inv()) or USER_SELECTION
                        }
                    db.update(
                        "app_proxy_entries",
                        ContentValues().apply { put("flags", newFlags) },
                        "mode = ? AND pkg_name = ?",
                        arrayOf(mode, packageName),
                    )
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
                        if ((flags and FORCE_DESELECTION) == 0 && flags != 0) {
                            add(cursor.getString(0))
                        }
                    }
                }
            }
        }

    private fun installedApps(): List<NativeInstalledApp> {
        val pm = context.packageManager
        val flags = PackageManager.ApplicationInfoFlags.of(PackageManager.MATCH_ALL.toLong())
        return pm.getInstalledApplications(flags)
            .asSequence()
            .filter { it.packageName != context.packageName && it.enabled }
            .map { info ->
                NativeInstalledApp(
                    packageName = info.packageName,
                    label = runCatching { pm.getApplicationLabel(info).toString() }
                        .getOrElse { info.packageName },
                    system = (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                )
            }
            .distinctBy { it.packageName }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
            .toList()
    }

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
