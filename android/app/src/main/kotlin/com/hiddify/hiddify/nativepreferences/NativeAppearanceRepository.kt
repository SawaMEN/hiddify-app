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

enum class NativeAccentColor(val value: String) {
    GRAY("gray"), BLUE("blue"), VIOLET("violet"), ROSE("rose"), AMBER("amber");
}

/** Uses the persisted theme preference without changing other general settings. */
class NativeAppearanceRepository(context: Context) {
    private val preferences = context.getSharedPreferences("FlutterSharedPreferences", Context.MODE_PRIVATE)

    fun load(): NativeThemeMode {
        val value = preferences.all["flutter.theme_mode"] as? String
        return NativeThemeMode.entries.find { it.value == value } ?: NativeThemeMode.DARK
    }

    fun loadAccent(): NativeAccentColor = NativeAccentColor.entries.find {
        it.value == preferences.all["native.accent_color"]
    } ?: NativeAccentColor.GRAY

    fun saveAccent(color: NativeAccentColor): NativeAccentColor {
        check(preferences.edit().putString("native.accent_color", color.value).commit()) { "Could not save accent color" }
        return color
    }

    fun save(mode: NativeThemeMode): NativeThemeMode {
        check(preferences.edit().putString("flutter.theme_mode", mode.value).commit()) { "Could not save application theme" }
        return mode
    }
}
