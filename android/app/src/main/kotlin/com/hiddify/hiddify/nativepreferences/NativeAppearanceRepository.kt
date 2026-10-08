package com.hiddify.hiddify.nativepreferences

import android.content.Context

enum class NativeThemeMode(val value: String) {
    SYSTEM("system"), LIGHT("light"), DARK("dark"), BLACK("black");

    fun isDark(systemDark: Boolean): Boolean = when (this) {
        SYSTEM -> systemDark
        LIGHT -> false
        DARK, BLACK -> true
    }
}

/** Uses the compatibility UI's theme preference without changing other general settings. */
class NativeAppearanceRepository(context: Context) {
    private val preferences = context.getSharedPreferences("FlutterSharedPreferences", Context.MODE_PRIVATE)

    fun load(): NativeThemeMode {
        val value = preferences.all["flutter.theme_mode"] as? String
        return NativeThemeMode.entries.find { it.value == value } ?: NativeThemeMode.DARK
    }

    fun save(mode: NativeThemeMode): NativeThemeMode {
        check(preferences.edit().putString("flutter.theme_mode", mode.value).commit()) { "Could not save application theme" }
        return mode
    }
}
