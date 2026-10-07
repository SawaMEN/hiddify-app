package com.hiddify.hiddify.nativeprivacy

import android.content.Context

class NativePrivacySetupRepository(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("FlutterSharedPreferences", Context.MODE_PRIVATE)

    fun isConfigured(): Boolean = runCatching { NativePrivacySetup.isConfigured(preferences.all) }.getOrDefault(false)
    fun canRestore(): Boolean = NativePrivacySetup.canRestore(preferences.all)

    /** Caller holds the native core lifecycle barrier and has checked that it is stopped. */
    fun apply(restore: Boolean) {
        val values = preferences.all
        val changes = if (restore) NativePrivacySetup.restore(values) else NativePrivacySetup.configure(values)
        val editor = preferences.edit()
        changes.forEach { (key, value) -> when (value) {
            is Boolean -> editor.putBoolean(key, value)
            is String -> editor.putString(key, value)
            is Long -> editor.putLong(key, value)
            else -> error("Unsupported preference type")
        } }
        check(editor.commit()) { "Could not save privacy setup" }
    }
}
